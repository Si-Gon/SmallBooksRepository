package com.microservice.gateway.microservice_gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.cors.reactive.CorsWebFilter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de integración de CorsConfig.
 *
 * Verifica que el filtro CORS reactivo se configura correctamente con
 * la lista blanca de orígenes definida en msvc-gateway.yml.
 *
 * Usa @SpringBootTest para levantar el contexto real de Spring
 * (misma técnica que RateLimitingConfigTest).
 *
 * Estrategia de pruebas:
 * - Validamos que el bean CorsWebFilter existe en el contexto
 * - Validamos que las propiedades gateway.cors.allowed-origins se cargan
 * - Validamos que CorsConfig inyecta correctamente los orígenes
 * - NO probamos el comportamiento runtime del filtro (preflight, etc.)
 *   porque CorsWebFilter opera sobre ServerWebExchange reactivo y mockear
 *   ese flujo no es fiable. El comportamiento real se verifica en
 *   pruebas de integración con docker-compose.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class CorsConfigTest {

    @Autowired(required = false)
    private CorsWebFilter corsWebFilter;

    @Autowired
    private Environment environment;

    @Autowired
    private CorsConfig corsConfig;

    // ═══════════════════════════════════════════════════════════════
    // VERIFICACIÓN DEL BEAN
    // ═══════════════════════════════════════════════════════════════

    @Test
    void corsWebFilter_beanExiste() {
        assertThat(corsWebFilter)
                .as("CorsWebFilter debe ser un bean registrado en el contexto Spring")
                .isNotNull();
    }

    @Test
    void corsConfig_beanExiste() {
        assertThat(corsConfig)
                .as("CorsConfig debe ser un bean registrado (configuración CORS)")
                .isNotNull();
    }

    // ═══════════════════════════════════════════════════════════════
    // VERIFICACIÓN DE PROPIEDADES DESDE ENVIRONMENT
    // ═══════════════════════════════════════════════════════════════

    @Test
    void allowedOrigins_configuradosDesdeProperties() {
        String origins = environment.getProperty("gateway.cors.allowed-origins");
        assertThat(origins)
                .as("gateway.cors.allowed-origins debe estar configurado")
                .isNotNull()
                .isNotEmpty();
    }

    @Test
    void allowedOrigins_incluyeLocalhost4200() {
        String origins = environment.getProperty("gateway.cors.allowed-origins");
        assertThat(origins).contains("http://localhost:4200");
    }

    @Test
    void allowedOrigins_incluyeLocalhost3000() {
        String origins = environment.getProperty("gateway.cors.allowed-origins");
        assertThat(origins).contains("http://localhost:3000");
    }

    @Test
    void allowedOrigins_incluyeLocalhost5173() {
        String origins = environment.getProperty("gateway.cors.allowed-origins");
        assertThat(origins).contains("http://localhost:5173");
    }

    @Test
    void allowedOrigins_noIncluyeWildcard() {
        String origins = environment.getProperty("gateway.cors.allowed-origins");
        assertThat(origins)
                .as("allowed-origins NO debe contener '*' — riesgo de seguridad")
                .doesNotContain("*");
    }

    // ═══════════════════════════════════════════════════════════════
    // VERIFICACIÓN DE INYECCIÓN EN CorsConfig
    // ═══════════════════════════════════════════════════════════════

    @Test
    void corsConfig_allowedOrigins_listaEspecifica() {
        List<String> origins = corsConfig.getAllowedOriginsForTest();
        assertThat(origins)
                .isNotNull()
                .isNotEmpty()
                .contains("http://localhost:4200", "http://localhost:3000", "http://localhost:5173");
    }

    @Test
    void corsConfig_allowedOrigins_noContieneWildcard() {
        assertThat(corsConfig.getAllowedOriginsForTest())
                .as("Los orígenes permitidos no deben contener '*'")
                .doesNotContain("*");
    }
}
