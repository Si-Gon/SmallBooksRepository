package com.silvio.elending.client;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests del FeignRequestInterceptor.
 *
 * Verifica que el interceptor propaga el token JWT desde el
 * SecurityContextHolder al header Authorization de cada request Feign.
 */
class FeignRequestInterceptorTest {

    private final FeignRequestInterceptor interceptor = new FeignRequestInterceptor();

    @AfterEach
    void tearDown() {
        // Limpia el contexto después de cada test para evitar fuga entre tests
        SecurityContextHolder.clearContext();
    }

    // =========================================================
    // Casos felices
    // =========================================================

    @Test
    void apply_conTokenValido_debeAgregarHeaderAuthorization() {
        // Given — SecurityContextHolder con token JWT en credentials
        String token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c3VhcmlvMSJ9.firma";
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(null, token, null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        RequestTemplate template = new RequestTemplate();

        // When
        interceptor.apply(template);

        // Then
        Collection<String> headers = template.headers().get("Authorization");
        assertNotNull(headers, "Debe existir el header Authorization");
        assertEquals(1, headers.size());
        assertEquals("Bearer " + token, headers.iterator().next());
    }

    @Test
    void apply_conTokenConCaracteresEspeciales_debeAgregarHeaderCorrecto() {
        // Given — token con caracteres especiales comunes en JWT reales
        String token = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c3VhcmlvQHRlc3QuY29tIiwiaWF0IjoxNjg5MDAwMDAwfQ.signature_abc123-_.";
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(null, token);
        SecurityContextHolder.getContext().setAuthentication(auth);

        RequestTemplate template = new RequestTemplate();

        // When
        interceptor.apply(template);

        // Then
        Collection<String> headers = template.headers().get("Authorization");
        assertNotNull(headers);
        assertEquals("Bearer " + token, headers.iterator().next());
    }

    // =========================================================
    // Casos sin autenticación
    // =========================================================

    @Test
    void apply_sinAuthentication_noDebeAgregarHeader() {
        // Given — SecurityContextHolder vacío (null)
        SecurityContextHolder.getContext().setAuthentication(null);

        RequestTemplate template = new RequestTemplate();

        // When
        interceptor.apply(template);

        // Then
        assertNull(template.headers().get("Authorization"),
                "No debe agregar header Authorization si no hay authentication");
    }

    @Test
    void apply_conAuthenticationNullCredentials_noDebeAgregarHeader() {
        // Given — authentication existe pero credentials es null
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(null, null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        RequestTemplate template = new RequestTemplate();

        // When
        interceptor.apply(template);

        // Then
        assertNull(template.headers().get("Authorization"),
                "No debe agregar header si credentials es null");
    }

    // =========================================================
    // Casos borde — token vacío o en blanco
    // =========================================================

    @Test
    void apply_conTokenVacio_noDebeAgregarHeader() {
        // Given — token es String vacío
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(null, "");
        SecurityContextHolder.getContext().setAuthentication(auth);

        RequestTemplate template = new RequestTemplate();

        // When
        interceptor.apply(template);

        // Then
        assertNull(template.headers().get("Authorization"),
                "No debe agregar header si el token es vacío");
    }

    @Test
    void apply_conTokenSoloEspacios_noDebeAgregarHeader() {
        // Given — token con solo espacios en blanco
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(null, "   ");
        SecurityContextHolder.getContext().setAuthentication(auth);

        RequestTemplate template = new RequestTemplate();

        // When
        interceptor.apply(template);

        // Then
        assertNull(template.headers().get("Authorization"),
                "No debe agregar header si el token es solo espacios (isBlank)");
    }

    // =========================================================
    // Caso borde — credentials no es String
    // =========================================================

    @Test
    void apply_conCredentialsNoString_noDebeAgregarHeader() {
        // Given — credentials es Integer (tipo inesperado)
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(null, 12345);
        SecurityContextHolder.getContext().setAuthentication(auth);

        RequestTemplate template = new RequestTemplate();

        // When
        interceptor.apply(template);

        // Then
        assertNull(template.headers().get("Authorization"),
                "No debe agregar header si credentials no es String (ClassCastException evitada)");
    }

    // =========================================================
    // Verificación de que el interceptor no modifica otros headers
    // =========================================================

    @Test
    void apply_conTokenValido_noDebeModificarOtrosHeaders() {
        // Given — template con header existente
        String token = "token-valido";
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(null, token);
        SecurityContextHolder.getContext().setAuthentication(auth);

        RequestTemplate template = new RequestTemplate();
        template.header("Content-Type", "application/json");
        template.header("Accept", "application/json");

        // When
        interceptor.apply(template);

        // Then — headers preexistentes se conservan
        Collection<String> contentType = template.headers().get("Content-Type");
        assertNotNull(contentType);
        assertEquals("application/json", contentType.iterator().next());

        Collection<String> accept = template.headers().get("Accept");
        assertNotNull(accept);
        assertEquals("application/json", accept.iterator().next());

        // Authorization se agregó correctamente
        Collection<String> authHeaders = template.headers().get("Authorization");
        assertNotNull(authHeaders);
        assertEquals("Bearer " + token, authHeaders.iterator().next());

        // Total: 3 headers (Content-Type, Accept, Authorization)
        assertEquals(3, template.headers().size());
    }

    // =========================================================
    // Contexto limpiado entre tests — verificación implícita
    // =========================================================

    @Test
    void apply_conAuthenticationLuegoSinAuthentication_estanAislados() {
        // Given — primero con token
        String token = "token-aislamiento";
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(null, token);
        SecurityContextHolder.getContext().setAuthentication(auth);

        RequestTemplate template1 = new RequestTemplate();
        interceptor.apply(template1);
        assertNotNull(template1.headers().get("Authorization"));

        // When — limpiamos contexto (simula finally de JwtAuthenticationFilter)
        SecurityContextHolder.clearContext();

        RequestTemplate template2 = new RequestTemplate();
        interceptor.apply(template2);

        // Then — el segundo request NO tiene token
        assertNull(template2.headers().get("Authorization"),
                "Sin authentication, no debe propagar token");
    }
}
