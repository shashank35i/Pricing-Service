package com.goon.pricingservice.service;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class FareCalculationService {

    public BigDecimal estimateFare(
            double pickupLat,
            double pickupLon,
            double dropLat,
            double dropLon,
            String vehicleType) {

        return new BigDecimal("100.00");
    }
}