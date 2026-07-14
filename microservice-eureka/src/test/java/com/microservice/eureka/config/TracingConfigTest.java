package com.microservice.eureka.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import zipkin2.reporter.BytesMessageSender;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TracingConfigTest {

    @Autowired(required = false)
    private ObservationRegistry observationRegistry;

    @Autowired(required = false)
    private Tracer tracer;

    @Autowired(required = false)
    private BytesMessageSender zipkinSender;

    @Autowired
    private Environment environment;

    @Test
    void observationRegistry_beanExiste() {
        assertNotNull(observationRegistry,
                "ObservationRegistry debe estar disponible (auto-config Micrometer)");
    }

    @Test
    void tracer_beanExiste() {
        assertNotNull(tracer,
                "Tracer (Brave) debe estar disponible (auto-config Micrometer)");
    }

    @Test
    void contextCarga_tracingBeansDisponibles() {
        assertAll(
                () -> assertNotNull(observationRegistry),
                () -> assertNotNull(tracer)
        );
    }

    @Test
    void zipkinSender_beanExiste() {
        assertNotNull(zipkinSender,
                "Sender de Zipkin debe estar registrado para exportar trazas");
    }

    @Test
    void zipkinEndpoint_configurado() {
        // Verifica que el endpoint de Zipkin esté configurado en el entorno
        String endpoint = environment.getProperty("management.zipkin.tracing.endpoint");
        assertNotNull(endpoint,
                "management.zipkin.tracing.endpoint debe estar configurado");
        assertFalse(endpoint.isBlank(),
                "El endpoint de Zipkin no debe estar vacío");
        assertTrue(endpoint.contains("zipkin"),
                "El endpoint debe contener 'zipkin' (servicio Docker): " + endpoint);
    }
}
