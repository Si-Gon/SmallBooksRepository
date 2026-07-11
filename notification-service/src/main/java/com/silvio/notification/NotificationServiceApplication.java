package com.silvio.notification;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// @EnableRabbit — activa el consumo de eventos desde RabbitMQ
// Escucha la cola "notificacion.queue" para procesar notificaciones
// de forma asíncrona, reemplazando la llamada Feign síncrona anterior.
@SpringBootApplication
@EnableRabbit
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}