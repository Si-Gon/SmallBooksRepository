package com.silvio.elending.config;

import com.silvio.elending.client.CatalogClientFallbackFactory;
import com.silvio.elending.client.IdentityClientFallbackFactory;
import com.silvio.elending.client.LicenseClientFallbackFactory;
import com.silvio.elending.client.SubscriptionClientFallbackFactory;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

// Tests de integración para Resilience4j Circuit Breaker
// Verifica que la configuración se cargue correctamente en el contexto Spring
// y que los beans de fallback factory estén registrados.
@SpringBootTest
@ActiveProfiles("test")
class Resilience4jConfigIntegrationTest {

    @Autowired(required = false)
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired(required = false)
    private IdentityClientFallbackFactory identityClientFallbackFactory;

    @Autowired(required = false)
    private CatalogClientFallbackFactory catalogClientFallbackFactory;

    @Autowired(required = false)
    private SubscriptionClientFallbackFactory subscriptionClientFallbackFactory;

    @Autowired(required = false)
    private LicenseClientFallbackFactory licenseClientFallbackFactory;

    @Test
    void contextLoads_conCircuitBreakerConfig() {
        // Verifica que el contexto Spring se carga sin errores
        // con la configuración de Resilience4j
        assertNotNull(circuitBreakerRegistry,
                "CircuitBreakerRegistry debe estar disponible en el contexto");
    }

    @Test
    void circuitBreakerRegistry_contieneInstanciaIdentityService() {
        assertNotNull(circuitBreakerRegistry);
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("identity-service");
        assertNotNull(cb, "Debe existir un circuito para identity-service");
        assertEquals("identity-service", cb.getName());
    }

    @Test
    void circuitBreakerRegistry_contieneInstanciaCatalogService() {
        assertNotNull(circuitBreakerRegistry);
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("catalog-service");
        assertNotNull(cb, "Debe existir un circuito para catalog-service");
        assertEquals("catalog-service", cb.getName());
    }

    @Test
    void circuitBreakerRegistry_contieneInstanciaSubscriptionService() {
        assertNotNull(circuitBreakerRegistry);
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("subscription-service");
        assertNotNull(cb, "Debe existir un circuito para subscription-service");
        assertEquals("subscription-service", cb.getName());
    }

    @Test
    void circuitBreakerRegistry_contieneInstanciaLicenseService() {
        assertNotNull(circuitBreakerRegistry);
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("license-service");
        assertNotNull(cb, "Debe existir un circuito para license-service");
        assertEquals("license-service", cb.getName());
    }

    @Test
    void circuitBreakerRegistry_configDefault_slidingWindowSize10() {
        assertNotNull(circuitBreakerRegistry);
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("identity-service");
        assertEquals(10, cb.getCircuitBreakerConfig().getSlidingWindowSize(),
                "sliding-window-size debe ser 10");
    }

    @Test
    void circuitBreakerRegistry_configDefault_failureRateThreshold50() {
        assertNotNull(circuitBreakerRegistry);
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("identity-service");
        assertEquals(50f, cb.getCircuitBreakerConfig().getFailureRateThreshold(),
                "failure-rate-threshold debe ser 50%");
    }

    @Test
    void circuitBreakerRegistry_configDefault_permittedCallsInHalfOpen3() {
        assertNotNull(circuitBreakerRegistry);
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("identity-service");
        assertEquals(3, cb.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState(),
                "permitted-number-of-calls-in-half-open-state debe ser 3");
    }

    @Test
    void circuitBreakerRegistry_configDefault_minimumNumberOfCalls5() {
        assertNotNull(circuitBreakerRegistry);
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("identity-service");
        assertEquals(5, cb.getCircuitBreakerConfig().getMinimumNumberOfCalls(),
                "minimum-number-of-calls debe ser 5");
    }

    @Test
    void fallbackFactory_identityClient_beanRegistrado() {
        assertNotNull(identityClientFallbackFactory,
                "IdentityClientFallbackFactory debe ser un bean registrado en Spring");
    }

    @Test
    void fallbackFactory_catalogClient_beanRegistrado() {
        assertNotNull(catalogClientFallbackFactory,
                "CatalogClientFallbackFactory debe ser un bean registrado en Spring");
    }

    @Test
    void fallbackFactory_subscriptionClient_beanRegistrado() {
        assertNotNull(subscriptionClientFallbackFactory,
                "SubscriptionClientFallbackFactory debe ser un bean registrado en Spring");
    }

    @Test
    void fallbackFactory_licenseClient_beanRegistrado() {
        assertNotNull(licenseClientFallbackFactory,
                "LicenseClientFallbackFactory debe ser un bean registrado en Spring");
    }

    @Test
    void cuatroCircuitosConMismaConfig_baseConfigDefault() {
        assertNotNull(circuitBreakerRegistry);
        CircuitBreaker cbIdentity = circuitBreakerRegistry.circuitBreaker("identity-service");
        CircuitBreaker cbCatalog = circuitBreakerRegistry.circuitBreaker("catalog-service");
        CircuitBreaker cbSubscription = circuitBreakerRegistry.circuitBreaker("subscription-service");
        CircuitBreaker cbLicense = circuitBreakerRegistry.circuitBreaker("license-service");

        // Los 4 circuitos deben tener la misma configuración (base-config: default)
        assertEquals(
                cbIdentity.getCircuitBreakerConfig().getSlidingWindowSize(),
                cbCatalog.getCircuitBreakerConfig().getSlidingWindowSize());
        assertEquals(
                cbIdentity.getCircuitBreakerConfig().getFailureRateThreshold(),
                cbSubscription.getCircuitBreakerConfig().getFailureRateThreshold());
        assertEquals(
                cbIdentity.getCircuitBreakerConfig().getMinimumNumberOfCalls(),
                cbLicense.getCircuitBreakerConfig().getMinimumNumberOfCalls());
        assertEquals(
                cbIdentity.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState(),
                cbLicense.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState());
    }
}
