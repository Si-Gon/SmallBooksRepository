package com.silvio.subscription.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
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
    private ObservedAspect observedAspect;

    @Autowired(required = false)
    private ObservationRegistry observationRegistry;

    @Autowired(required = false)
    private Tracer tracer;

    @Autowired(required = false)
    private BytesMessageSender zipkinSender;

    @Autowired
    private Environment environment;

    @Test
    void observedAspect_beanExiste() {
        assertNotNull(observedAspect);
    }

    @Test
    void observationRegistry_beanExiste() {
        assertNotNull(observationRegistry);
    }

    @Test
    void observedAspect_usaObservationRegistry() {
        ObservedAspect aspecto = new ObservedAspect(observationRegistry);
        assertNotNull(aspecto);
    }

    @Test
    void tracer_beanExiste() {
        assertNotNull(tracer);
    }

    @Test
    void zipkinSender_beanExiste() {
        assertNotNull(zipkinSender);
    }

    @Test
    void zipkinEndpoint_configurado() {
        String endpoint = environment.getProperty("management.zipkin.tracing.endpoint");
        assertNotNull(endpoint);
        assertFalse(endpoint.isBlank());
        assertTrue(endpoint.contains("zipkin"));
    }
}
