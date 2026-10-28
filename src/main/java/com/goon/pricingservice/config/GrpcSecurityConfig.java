package com.goon.pricingservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.grpc.server.security.AuthenticationProcessInterceptor;
import org.springframework.grpc.server.security.GrpcSecurity;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Configuration
public class GrpcSecurityConfig {

    @Bean
    @GlobalServerInterceptor
    AuthenticationProcessInterceptor internalPricingSecurity(
            GrpcSecurity grpc) throws Exception {

        return grpc
                .authorizeRequests(requests -> requests

                        .methods("goon.pricing.PricingService/GetFareQuote")
                        .access((authentication, context) ->
                                check(authentication.get(),
                                        "goon-services", "pricing.quote"))

                        .methods("goon.pricing.PricingService/CalculateFinalFare")
                        .access((authentication, context) ->
                                check(authentication.get(),
                                        "goon-services", "pricing.final"))

                        .allRequests()
                        .denyAll())

                .oauth2ResourceServer(resourceServer ->
                        resourceServer.jwt(Customizer.withDefaults()))

                .build();
    }

    private AuthorizationDecision check(
            Authentication authentication,
            String clientId,
            String scope) {

        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            return new AuthorizationDecision(false);
        }

        boolean hasScope = jwt.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority()
                        .equals("SCOPE_" + scope));

        boolean trustedClient = clientId.equals(
                jwt.getToken().getClaimAsString("azp")
        );

        return new AuthorizationDecision(hasScope && trustedClient);
    }
}