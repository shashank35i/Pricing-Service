package com.goon.pricingservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient osrmRestClient(
            RestClient.Builder builder,
            @Value("${app.osrm.url}") String url) {

        return builder
                .baseUrl(url)
                .build();
    }
}
