package com.goon.pricingservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class FareCalculationService {

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);
    private static final long FREE_WAIT_SECONDS = 120;

    private final RestClient osrmClient;



    public BigDecimal estimateFare(
            double pickupLat,
            double pickupLon,
            double dropLat,
            double dropLon,
            String vehicleType
    ) {
        validate(pickupLat, pickupLon);
        validate(dropLat, dropLon);

        Rate rate = rateFor(vehicleType);

        String path = String.format(
                Locale.US,
                "/route/v1/driving/%f,%f;%f,%f?overview=false",
                pickupLon, pickupLat,
                dropLon, dropLat
        );

        OsrmResponse response = osrmClient.get()
                .uri(URI.create(path))
                .retrieve()
                .body(OsrmResponse.class);

        if (response == null
                || !"Ok".equals(response.code())
                || response.routes() == null
                || response.routes().length == 0
                || response.routes()[0] == null) {

            throw new IllegalStateException(
                    "OSRM could not calculate the route"
            );
        }

        double meters = response.routes()[0].distance();

        if (!Double.isFinite(meters) || meters < 0) {
            throw new IllegalStateException(
                    "Invalid OSRM route distance"
            );
        }

        BigDecimal km = BigDecimal.valueOf(meters)
                .divide(THOUSAND, 6, RoundingMode.HALF_UP);

        return km.multiply(rate.perKm())
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateFinalFare(
            BigDecimal quotedFare,
            long waitingTimeS,
            String vehicleType
    ) {
        if (quotedFare == null || quotedFare.signum() < 0) {
            throw new IllegalArgumentException("Invalid quoted fare");
        }

        if (waitingTimeS < 0) {
            throw new IllegalArgumentException("Invalid waiting time");
        }

        Rate rate = rateFor(vehicleType);

        long billableSeconds = Math.max(
                0,
                waitingTimeS - FREE_WAIT_SECONDS
        );

        BigDecimal waitingFare = BigDecimal.valueOf(billableSeconds)
                .multiply(rate.waitPerMinute())
                .divide(SIXTY, 2, RoundingMode.HALF_UP);

        return quotedFare.add(waitingFare)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private Rate rateFor(String vehicleType) {

        if (vehicleType == null) {
            throw new IllegalArgumentException("Vehicle type required");
        }

        return switch (vehicleType.toUpperCase(Locale.ROOT)) {
            case "BIKE" -> new Rate("8", "1");
            case "AUTO" -> new Rate("12", "2");
            case "MINI" -> new Rate("15", "3");
            case "SEDAN" -> new Rate("18", "4");
            case "SUV" -> new Rate("24", "5");
            default -> throw new IllegalArgumentException(
                    "Unsupported vehicle type: " + vehicleType
            );
        };
    }

    private void validate(double lat, double lon) {
        if (!Double.isFinite(lat)
                || !Double.isFinite(lon)
                || lat < -90 || lat > 90
                || lon < -180 || lon > 180) {

            throw new IllegalArgumentException("Invalid coordinates");
        }
    }

    private record Rate(
            BigDecimal perKm,
            BigDecimal waitPerMinute
    ) {
        Rate(String km, String waiting) {
            this(new BigDecimal(km), new BigDecimal(waiting));
        }
    }

    private record OsrmResponse(
            String code,
            OsrmRoute[] routes
    ) {}

    private record OsrmRoute(
            double distance
    ) {}
}