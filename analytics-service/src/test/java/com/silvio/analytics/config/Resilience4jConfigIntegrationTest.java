package com.silvio.analytics.config;

import com.silvio.analytics.client.LendingClient;
import com.silvio.analytics.client.LendingClientFallbackFactory;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

// Tests de integracion para Resilience4j Circuit Breaker en analytics-service
// Verifica que la configuracion se cargue correctamente en el contexto Spring
// y que el bean de fallback factory este registrado.
@SpringBootTest
@ActiveProfiles("test")
class Resilience4jConfigIntegrationTest {

    @Autowired(required = false)
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired(required = false)
    private LendingClientFallbackFactory lendingClientFallbackFactory;

    // Los Feign clients se mockean porque sin Eureka/registro no pueden crearse
    @MockBean
    private LendingClient lendingClient;

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
}
