package com.microservice.gateway.microservice_gateway.filter;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica que JwtAuthFilter usa la API de jjwt 0.12.x (parseSignedClaims / getPayload)
 * y no la API deprecada de jjwt 0.11.x (parseClaimsJws / getBody).
 *
 * Es un test de regresión estático: si alguien reintroduce la API deprecada
 * en el futuro, este test fallará en CI.
 */
class JwtAuthFilterApiMigrationTest {

    private static final Path FILTER_SOURCE = Paths.get(
            "src/main/java/com/microservice/gateway/microservice_gateway/filter/JwtAuthFilter.java");

    private String leerFuente() {
        try {
            return Files.readString(FILTER_SOURCE);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo leer " + FILTER_SOURCE, e);
        }
    }

    @Test
    void jwtAuthFilter_usaParseSignedClaims_noParseClaimsJws() {
        String source = leerFuente();
        assertTrue(source.contains("parseSignedClaims"),
                "JwtAuthFilter.java debe usar parseSignedClaims (jjwt 0.12.x)");
        assertFalse(source.contains("parseClaimsJws"),
                "JwtAuthFilter.java NO debe usar parseClaimsJws (deprecado en jjwt 0.12.x)");
    }

    @Test
    void jwtAuthFilter_usaGetPayload_noGetBody() {
        String source = leerFuente();
        assertTrue(source.contains(".getPayload()"),
                "JwtAuthFilter.java debe usar .getPayload() (jjwt 0.12.x)");
        assertFalse(source.contains(".getBody()"),
                "JwtAuthFilter.java NO debe usar .getBody() (deprecado en jjwt 0.12.x)");
    }
}
