package com.goon.pricingservice.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
public class QuoteSigner {

    private final SecretKey key;

    public QuoteSigner(@Value("${app.secret-key}") String secretKey) {
        byte[] bytes = Base64.getDecoder().decode(secretKey);
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String createQuote(
            String customerId,
            double pickupLat,
            double pickupLon,
            double dropLat,
            double dropLon,
            String vehicleType,
            BigDecimal estimatedFare) {

        Instant now = Instant.now();

        return Jwts.builder()
                .header()
                    .type("JWT")
                    .and()
                .id(UUID.randomUUID().toString())
                .subject(customerId)
                .claim("pickupLat", pickupLat)
                .claim("pickupLon", pickupLon)
                .claim("dropLat", dropLat)
                .claim("dropLon", dropLon)
                .claim("vehicleType", vehicleType)
                .claim("estimatedFare", estimatedFare)
                .claim("currency", "INR")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}