package com.ahmedsohail.transferservice.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Describes the public API for the generated OpenAPI document and Swagger UI.
 * springdoc serves the spec at {@code /v3/api-docs} and a UI at {@code /swagger-ui.html}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI transferServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Transfer Service API")
                        .description("Records money transfers and orchestrates their settlement by "
                                + "calling the account-service over HTTP.")
                        .version("0.1.0"));
    }
}
