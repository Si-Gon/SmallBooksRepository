package com.silvio.elending.security;

import com.silvio.elending.exception.TokenExtraccionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests del JwtExtractor.
 *
 * Extrae el username del campo "sub" del payload JWT.
 * No valida la firma — solo parsea el Base64 del payload.
 */
class JwtExtractorTest {

    private final JwtExtractor jwtExtractor = new JwtExtractor();

    @Test
    void extraerUsuario_conTokenValido_debeRetornarUsername() {
        // Given — JWT con payload {"sub":"usuario1"}
        // Header: {"alg":"HS256"}
        // Payload: {"sub":"usuario1"}
        // Firma: fake
        String token = "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c3VhcmlvMSJ9.firma";

        // When
        String usuario = jwtExtractor.extraerUsuario(token);

        // Then
        assertEquals("usuario1", usuario);
    }

    @Test
    void extraerUsuario_conTokenConSubLargo_debeRetornarUsername() {
        // Given — payload: {"sub":"usuario_con_nombre_largo_123"}
        String token = "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c3VhcmlvX2Nvbl9ub21icmVfbGFyZ29fMTIzIn0.firma";

        // When
        String usuario = jwtExtractor.extraerUsuario(token);

        // Then
        assertEquals("usuario_con_nombre_largo_123", usuario);
    }

    @Test
    void extraerUsuario_conTokenInvalido_debeLanzarExcepcion() {
        // Given — token sin payload válido
        String tokenInvalido = "Bearer token-invalido";

        // When & Then
        TokenExtraccionException ex = assertThrows(TokenExtraccionException.class,
                () -> jwtExtractor.extraerUsuario(tokenInvalido));
        assertTrue(ex.getMessage().contains("No se pudo extraer"));
    }

    @Test
    void extraerUsuario_conHeaderSinBearer_debeLanzarExcepcion() {
        // Given — header sin "Bearer "
        String tokenSinBearer = "token-sin-bearer";

        // When & Then
        assertThrows(TokenExtraccionException.class,
                () -> jwtExtractor.extraerUsuario(tokenSinBearer));
    }

    @Test
    void extraerUsuario_conPayloadInvalido_debeLanzarExcepcion() {
        // Given — token con payload que no es JSON válido
        String token = "Bearer header.payload-invalido.firma";

        // When & Then
        assertThrows(TokenExtraccionException.class,
                () -> jwtExtractor.extraerUsuario(token));
    }
}