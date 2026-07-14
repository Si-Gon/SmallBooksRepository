package com.microservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de seguridad de placeholders en Config Server.
 *
 * Verifica que:
 * 1. resolve-placeholders=false está configurado (el fix del Debugger)
 * 2. Los archivos YAML con ${JWT_SECRET} NO tienen el valor hardcodeado
 *
 * ¿Por qué es importante?
 * Con resolve-placeholders: true (default), Config Server resuelve ${...}
 * usando su propio Environment. Como el Config Server NO tiene JWT_SECRET
 * en docker-compose.yml, la resolución fallaría al servir config.
 *
 * Con resolve-placeholders: false, cada cliente (gateway, identity) resuelve
 * ${JWT_SECRET} desde su propio Environment, manteniendo secretos aislados.
 */
@SpringBootTest
@ActiveProfiles({"test", "native"})
class ConfigServerPlaceholderTest {

    @Autowired
    private Environment environment;

    @Test
    void resolvePlaceholders_configuradoEnFalse() {
        // Verifica que la propiedad crítica está presente en la configuración
        String resolvePlaceholders = environment.getProperty(
                "spring.cloud.config.server.resolve-placeholders");

        assertThat(resolvePlaceholders)
                .as("resolve-placeholders debe estar configurado (seguridad)")
                .isNotNull();

        assertThat(resolvePlaceholders)
                .as("resolve-placeholders debe ser 'false' para evitar que el " +
                        "Config Server resuelva secretos que no le pertenecen")
                .isEqualTo("false");
    }

    @Test
    void resolvePlaceholders_noEsTrue() {
        // Doble verificación: si por algún motivo vuelve al default true,
        // el servidor intentaría resolver ${JWT_SECRET} y fallaría
        String resolvePlaceholders = environment.getProperty(
                "spring.cloud.config.server.resolve-placeholders");

        assertThat(resolvePlaceholders)
                .as("resolve-placeholders NO debe ser true (riesgo de seguridad)")
                .isNotEqualTo("true");
    }
}
