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
    AuthenticationProcessInterceptor internalPricingSecurity(GrpcSecurity grpc) throws Exception {
        return grpc
                .authorizeRequests(requests -> requests
                        .methods("goon.pricing.PricingService/GetFareQuote")
                        .access((authentication, context) -> {
                            Authentication current = authentication.get();
                            if (!(current instanceof JwtAuthenticationToken jwtAuthentication)) {
                                return new AuthorizationDecision(false);
                            }
                            boolean hasScope = jwtAuthentication.getAuthorities().stream()
                                    .anyMatch(authority -> authority.getAuthority().equals("SCOPE_pricing.quote"));
                            boolean trustedGateway = "api-gateway".equals(
                                    jwtAuthentication.getToken().getClaimAsString("azp"));
                            return new AuthorizationDecision(hasScope && trustedGateway);
                        })
                        .allRequests()
                        .denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                .build();
    }
}
