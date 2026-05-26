package com.silvio.content;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

// Content Delivery NO tiene BD propia
// Solo verifica préstamos via E-Lending y obtiene archivos via Ingestion

@SpringBootApplication
@EnableFeignClients
public class ContentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContentServiceApplication.class, args);
    }
}