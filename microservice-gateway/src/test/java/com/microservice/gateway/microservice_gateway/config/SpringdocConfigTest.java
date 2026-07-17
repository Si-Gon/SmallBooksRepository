package com.microservice.gateway.microservice_gateway.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de integración de la configuración Springdoc (Swagger).
 *
 * Verifica que Swagger UI y API docs están deshabilitados por defecto
 * (seguridad H-05) y solo se habilitan cuando SWAGGER_ENABLED=true.
 *
 * La configuración está en application.yml:
 *   springdoc.swagger-ui.enabled: ${SWAGGER_ENABLED:false}
 *   springdoc.api-docs.enabled: ${SWAGGER_ENABLED:false}
 *
 * En producción, sin variable SWAGGER_ENABLED, ambos deben ser false
 * para no exponer /swagger-ui.html ni /v3/api-docs.
 */
class SpringdocConfigTest {

    // ═══════════════════════════════════════════════════════════════════════════════
    // Contexto 1: Sin SWAGGER_ENABLED → propiedades deben ser false
    // ═══════════════════════════════════════════════════════════════════════════════

    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
    @ActiveProfiles("test")
    @Nested
    class SinSwaggerEnabled {

        @Autowired
        private Environment environment;

        @Test
        void swaggerUiDebeEstarDeshabilitado() {
            // DADO: No se ha definido la variable SWAGGER_ENABLED
            // CUANDO: Se lee la propiedad springdoc.swagger-ui.enabled
            // ENTONCES: Debe ser false (valor por defecto en la expresión YAML)
            String swaggerUiEnabled = environment.getProperty("springdoc.swagger-ui.enabled");
            assertThat(swaggerUiEnabled).isEqualTo("false");
        }

        @Test
        void apiDocsDebeEstarDeshabilitado() {
            // DADO: No se ha definido la variable SWAGGER_ENABLED
            // CUANDO: Se lee la propiedad springdoc.api-docs.enabled
            // ENTONCES: Debe ser false (valor por defecto en la expresión YAML)
            String apiDocsEnabled = environment.getProperty("springdoc.api-docs.enabled");
            assertThat(apiDocsEnabled).isEqualTo("false");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Contexto 2: Con SWAGGER_ENABLED=true → propiedades deben ser true
    // ═══════════════════════════════════════════════════════════════════════════════

    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
    @ActiveProfiles("test")
    @TestPropertySource(properties = {"SWAGGER_ENABLED=true"})
    @Nested
    class ConSwaggerEnabled {

        @Autowired
        private Environment environment;

        @Test
        void swaggerUiDebeEstarHabilitado() {
            // DADO: La variable SWAGGER_ENABLED=true está definida
            // CUANDO: Se lee la propiedad springdoc.swagger-ui.enabled
            // ENTONCES: Debe ser true (resuelto desde la expresión YAML)
            String swaggerUiEnabled = environment.getProperty("springdoc.swagger-ui.enabled");
            assertThat(swaggerUiEnabled).isEqualTo("true");
        }

        @Test
        void apiDocsDebeEstarHabilitado() {
            // DADO: La variable SWAGGER_ENABLED=true está definida
            // CUANDO: Se lee la propiedad springdoc.api-docs.enabled
            // ENTONCES: Debe ser true (resuelto desde la expresión YAML)
            String apiDocsEnabled = environment.getProperty("springdoc.api-docs.enabled");
            assertThat(apiDocsEnabled).isEqualTo("true");
        }
    }
}
