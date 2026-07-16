package com.silvio.identity.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica que JwtUtil usa la API de jjwt 0.12.x (parseSignedClaims / getPayload)
 * y no la API deprecada de jjwt 0.11.x (parseClaimsJws / getBody).
 *
 * Es un test de regresión estático: si alguien reintroduce la API deprecada
 * en el futuro, este test fallará en CI.
 */
class JwtApiMigrationTest {

    private static final Path JWT_UTIL_SOURCE = Paths.get(
            "src/main/java/com/silvio/identity/security/JwtUtil.java");

    private String leerFuente() {
        try {
            return Files.readString(JWT_UTIL_SOURCE);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo leer " + JWT_UTIL_SOURCE, e);
        }
    }

    @Test
    void jwtUtil_usaParseSignedClaims_noParseClaimsJws() {
        String source = leerFuente();
        assertTrue(source.contains("parseSignedClaims"),
                "JwtUtil.java debe usar parseSignedClaims (jjwt 0.12.x)");
        assertFalse(source.contains("parseClaimsJws"),
                "JwtUtil.java NO debe usar parseClaimsJws (deprecado en jjwt 0.12.x)");
    }

    @Test
    void jwtUtil_usaGetPayload_noGetBody() {
        String source = leerFuente();
        assertTrue(source.contains(".getPayload()"),
                "JwtUtil.java debe usar .getPayload() (jjwt 0.12.x)");
        assertFalse(source.contains(".getBody()"),
                "JwtUtil.java NO debe usar .getBody() (deprecado en jjwt 0.12.x)");
    }
}
