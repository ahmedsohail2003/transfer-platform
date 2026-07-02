package com.ahmedsohail.accountservice.common;

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
    public OpenAPI accountServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Account Service API")
                        .description("Owns customer accounts and balances, and enforces the no-overdraft invariant on debits.")
                        .version("0.1.0"));
    }
}
