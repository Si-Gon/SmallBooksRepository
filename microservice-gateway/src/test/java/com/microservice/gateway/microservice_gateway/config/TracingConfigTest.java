package com.microservice.gateway.microservice_gateway.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class TracingConfigTest {

    @Autowired(required = false)
    private ObservationRegistry observationRegistry;

    @Autowired(required = false)
    private Tracer tracer;

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
}
