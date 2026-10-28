package com.goon.pricingservice.grpc;

import com.goon.grpc.pricing.*;
import com.goon.pricingservice.config.QuoteSigner;
import com.goon.pricingservice.service.FareCalculationService;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;

import java.math.BigDecimal;
import java.math.RoundingMode;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class PricingGrpcService
        extends PricingServiceGrpc.PricingServiceImplBase {

    private final QuoteSigner quoteSigner;
    private final FareCalculationService fareService;

    @Override
    public void calculateFinalFare(FinalFareRequest request,
                                   StreamObserver<FinalFareResponse> observer) {
        try {
            java.util.UUID.fromString(request.getRideId());
            if (request.getQuotedFarePaise() < 0 || request.getWaitingTimeSeconds() < 0) {
                throw new IllegalArgumentException("Fare and waiting time must be nonnegative");
            }
            BigDecimal fare = fareService.calculateFinalFare(
                    BigDecimal.valueOf(request.getQuotedFarePaise(), 2),
                    request.getWaitingTimeSeconds(), request.getVehicleType());
            observer.onNext(FinalFareResponse.newBuilder()
                    .setFinalFarePaise(fare.movePointRight(2).longValueExact())
                    .setCurrency("INR").build());
            observer.onCompleted();
        } catch (IllegalArgumentException | ArithmeticException e) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription("Invalid final fare request")
                    .asRuntimeException());
        } catch (Exception e) {
            log.error("Final fare calculation failed", e);
            observer.onError(Status.INTERNAL.withDescription("Final fare calculation failed")
                    .asRuntimeException());
        }
    }

    @Override
    public void getFareQuote(
            FareQuoteRequest request,
            StreamObserver<FareQuoteResponse> observer) {

        try {
            validate(request);

            BigDecimal fare = fareService.estimateFare(
                    request.getPickupLat(),
                    request.getPickupLon(),
                    request.getDropLat(),
                    request.getDropLon(),
                    request.getVehicleType()
            );

            long paise = fare
                    .setScale(2, RoundingMode.HALF_UP)
                    .movePointRight(2)
                    .longValueExact();



            if (paise < 0) {
                throw new IllegalArgumentException("Invalid fare");
            }

            String signedQuote = quoteSigner.createQuote(
                    request.getCustomerId(),
                    request.getPickupLat(),
                    request.getPickupLon(),
                    request.getDropLat(),
                    request.getDropLon(),
                    request.getVehicleType(),
                    fare
            );

            FareQuoteResponse response = FareQuoteResponse.newBuilder()
                    .setEstimatedFare(paise)
                    .setCurrency("INR")
                    .setSignedQuote(signedQuote)
                    .build();

            observer.onNext(response);
            observer.onCompleted();

        } catch (IllegalArgumentException e) {
            observer.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription(e.getMessage())
                            .asRuntimeException()
            );
        } catch (Exception e) {
            log.error("Fare estimation failed", e);

            observer.onError(
                    Status.INTERNAL
                            .withDescription("Fare estimation failed")
                            .withCause(e)
                            .asRuntimeException()
            );
        }
    }

    private void validate(FareQuoteRequest r) {
        if (r.getCustomerId().isBlank() ||
                r.getVehicleType().isBlank() ||
                !validLat(r.getPickupLat()) ||
                !validLat(r.getDropLat()) ||
                !validLon(r.getPickupLon()) ||
                !validLon(r.getDropLon())) {
            throw new IllegalArgumentException("Invalid fare request");
        }
    }

    private boolean validLat(double v) {
        return Double.isFinite(v) && v >= -90 && v <= 90;
    }

    private boolean validLon(double v) {
        return Double.isFinite(v) && v >= -180 && v <= 180;
    }
}
