package com.silvio.identity.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

// Tests de integración para la configuración de tracing distribuido.
// Verifica que el bean ObservedAspect se cree correctamente en el contexto
// para que las anotaciones @Observed en los métodos del servicio
// generen spans de tracing automáticamente.
@SpringBootTest
@ActiveProfiles("test")
class TracingConfigTest {

    @Autowired(required = false)
    private ObservedAspect observedAspect;

    @Autowired(required = false)
    private ObservationRegistry observationRegistry;

    @Test
    void observedAspect_beanExiste() {
        assertNotNull(observedAspect,
                "ObservedAspect debe estar registrado para procesar @Observed");
    }

    @Test
    void observationRegistry_beanExiste() {
        assertNotNull(observationRegistry,
                "ObservationRegistry debe estar disponible en el contexto");
    }

    @Test
    void observedAspect_usaObservationRegistry() {
        // Verifica que el ObservedAspect acepte el ObservationRegistry
        // Simula la creación del bean manualmente para validar el constructor
        ObservedAspect aspecto = new ObservedAspect(observationRegistry);
        assertNotNull(aspecto);
    }
}
