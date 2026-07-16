package com.silvio.content.config;

import com.silvio.content.client.IngestionClient;
import com.silvio.content.client.IngestionClientFallbackFactory;
import com.silvio.content.client.LendingClient;
import com.silvio.content.client.LendingClientFallbackFactory;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

// Tests de integracion para Resilience4j Circuit Breaker en content-service
// Verifica que la configuracion se cargue correctamente en el contexto Spring
// y que los beans de fallback factory esten registrados.
@SpringBootTest
@ActiveProfiles("test")
class Resilience4jConfigIntegrationTest {

    @Autowired(required = false)
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired(required = false)
    private LendingClientFallbackFactory lendingClientFallbackFactory;

    @Autowired(required = false)
    private IngestionClientFallbackFactory ingestionClientFallbackFactory;

    // Los Feign clients se mockean porque sin Eureka/registro no pueden crearse
    @MockBean
    private LendingClient lendingClient;

    @MockBean
    private IngestionClient ingestionClient;

    @Test
    void contextLoads_conCircuitBreakerConfig() {
        // Verifica que el contexto Spring se carga sin errores
        assertNotNull(circuitBreakerRegistry,
                "CircuitBreakerRegistry debe estar disponible en el contexto");
    }

    @Test
    void circuitBreakerRegistry_contieneInstanciaElendingService() {
        assertNotNull(circuitBreakerRegistry);
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("elending-service");
        assertNotNull(cb, "Debe existir un circuito para elending-service");
        assertEquals("elending-service", cb.getName());
    }

    @Test
    void circuitBreakerRegistry_contieneInstanciaIngestionService() {
        assertNotNull(circuitBreakerRegistry);
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("ingestion-service");
        assertNotNull(cb, "Debe existir un circuito para ingestion-service");
        assertEquals("ingestion-service", cb.getName());
    }

    @Test
    void circuitBreakerRegistry_configDefault_slidingWindowSize10() {
        assertNotNull(circuitBreakerRegistry);
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("elending-service");
        assertEquals(10, cb.getCircuitBreakerConfig().getSlidingWindowSize(),
                "sliding-window-size debe ser 10");
    }

    @Test
    void circuitBreakerRegistry_configDefault_failureRateThreshold50() {
        assertNotNull(circuitBreakerRegistry);
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("elending-service");
        assertEquals(50f, cb.getCircuitBreakerConfig().getFailureRateThreshold(),
                "failure-rate-threshold debe ser 50%");
    }

    @Test
    void circuitBreakerRegistry_configDefault_permittedCallsInHalfOpen3() {
        assertNotNull(circuitBreakerRegistry);
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("elending-service");
        assertEquals(3, cb.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState(),
                "permitted-number-of-calls-in-half-open-state debe ser 3");
    }

    @Test
    void circuitBreakerRegistry_configDefault_minimumNumberOfCalls5() {
        assertNotNull(circuitBreakerRegistry);
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("elending-service");
        assertEquals(5, cb.getCircuitBreakerConfig().getMinimumNumberOfCalls(),
                "minimum-number-of-calls debe ser 5");
    }

    @Test
    void fallbackFactory_lendingClient_beanRegistrado() {
        assertNotNull(lendingClientFallbackFactory,
                "LendingClientFallbackFactory debe ser un bean registrado en Spring");
    }

    @Test
    void fallbackFactory_ingestionClient_beanRegistrado() {
        assertNotNull(ingestionClientFallbackFactory,
                "IngestionClientFallbackFactory debe ser un bean registrado en Spring");
    }

    @Test
    void ambosCircuitosConMismaConfig_baseConfigDefault() {
        assertNotNull(circuitBreakerRegistry);
        CircuitBreaker cbLending = circuitBreakerRegistry.circuitBreaker("elending-service");
        CircuitBreaker cbIngestion = circuitBreakerRegistry.circuitBreaker("ingestion-service");

        // Ambos circuitos deben tener la misma configuracion (base-config: default)
        assertEquals(
                cbLending.getCircuitBreakerConfig().getSlidingWindowSize(),
                cbIngestion.getCircuitBreakerConfig().getSlidingWindowSize());
        assertEquals(
                cbLending.getCircuitBreakerConfig().getFailureRateThreshold(),
                cbIngestion.getCircuitBreakerConfig().getFailureRateThreshold());
        assertEquals(
                cbLending.getCircuitBreakerConfig().getMinimumNumberOfCalls(),
                cbIngestion.getCircuitBreakerConfig().getMinimumNumberOfCalls());
        assertEquals(
                cbLending.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState(),
                cbIngestion.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState());
    }
}
