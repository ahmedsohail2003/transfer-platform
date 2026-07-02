package com.ahmedsohail.transferservice.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Supplies the {@link RestClient} used to reach account-service. The base URL is bound
 * from the {@code account-service.url} property (env {@code ACCOUNT_SERVICE_URL}),
 * defaulting to {@code http://localhost:8081} for local development.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient accountServiceRestClient(
            RestClient.Builder builder,
            @Value("${account-service.url:http://localhost:8081}") String accountServiceUrl) {
        return builder.baseUrl(accountServiceUrl).build();
    }
}
