package com.silvio.elending;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableFeignClients     → activa los clientes Feign (LicenseClient, SubscriptionClient)
// @EnableRabbit           → activa RabbitMQ (NotificacionPublisher envía eventos)
// @EnableScheduling       → activa el scheduler que cierra préstamos vencidos
//                           Sin esta anotación, @Scheduled no funciona
// @EnableSchedulerLock    → activa ShedLock para bloqueo distribuido
//                           Evita que múltiples instancias ejecuten el scheduler
//                           simultáneamente. Usa la tabla shedlock en la BD.

@SpringBootApplication
@EnableFeignClients
@EnableRabbit
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "30m")
public class PrestamoServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrestamoServiceApplication.class, args);
    }
}