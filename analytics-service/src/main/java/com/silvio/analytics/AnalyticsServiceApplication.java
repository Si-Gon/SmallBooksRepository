package com.silvio.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

// Analytics Service NO tiene base de datos propia
// Consulta al E-Lending Service via Feign y calcula métricas en memoria

@SpringBootApplication
@EnableFeignClients
public class AnalyticsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsServiceApplication.class, args);
    }
}