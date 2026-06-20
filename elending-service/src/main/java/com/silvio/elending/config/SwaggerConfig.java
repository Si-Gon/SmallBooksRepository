package com.silvio.elending.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("SmallBooks - E-Lending Service")
                .version("1.0.0")
                .description("Gestión de préstamos digitales con reglas BASICO y PREMIUM"));
    }
}