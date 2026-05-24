package com.silvio.elending;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableFeignClients  → activa los clientes Feign (LicenseClient)
// @EnableScheduling    → activa el scheduler que cierra préstamos vencidos
//                        Sin esta anotación, @Scheduled no funciona

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
public class PrestamoServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrestamoServiceApplication.class, args);
    }
}