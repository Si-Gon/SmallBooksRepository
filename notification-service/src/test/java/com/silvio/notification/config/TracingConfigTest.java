package com.silvio.notification.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TracingConfigTest {

    @Autowired(required = false)
    private ObservedAspect observedAspect;

    @Autowired(required = false)
    private ObservationRegistry observationRegistry;

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
}
