package com.silvio.ingestion.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Configuración de tracing distribuido con Micrometer.
// Define el bean ObservedAspect necesario para que las anotaciones @Observed
// en los métodos del servicio creen spans de tracing automáticamente.
// Cada span propaga el traceId a través de los headers B3/traceparent
// en llamadas HTTP (Feign) y mensajes AMQP (RabbitMQ).
@Configuration
public class TracingConfig {

    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }
}
