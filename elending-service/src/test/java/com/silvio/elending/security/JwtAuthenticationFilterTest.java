package com.silvio.elending.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests del JwtAuthenticationFilter.
 *
 * Verifica que el filtro extrae correctamente el token JWT del header
 * Authorization y lo almacena en SecurityContextHolder para que
 * FeignRequestInterceptor lo propague a los Feign Clients.
 *
 * IMPORTANTE: El finally del filtro ejecuta SecurityContextHolder.clearContext()
 * DESPUÉS de chain.doFilter() pero ANTES de que doFilterInternal retorne.
 * Por eso las verificaciones del Authentication se hacen DENTRO del
 * callback chain.doFilter(), no después de la llamada al filtro.
 */
class JwtAuthenticationFilterTest {

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // =========================================================
    // Casos felices
    // =========================================================

    @Test
    void doFilterInternal_conBearerToken_seteaAuthenticationDentroDelChain() throws Exception {
        // Given
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        String token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c3VhcmlvMSJ9.firma";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        // Capturar el estado dentro del chain (antes del finally)
        AtomicReference<Authentication> authRef = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            authRef.set(auth);
        };

        // When
        filter.doFilterInternal(request, response, chain);

        // Then — el token se almacenó durante la ejecución del chain
        Authentication authentication = authRef.get();
        assertNotNull(authentication, "Debe haber authentication durante chain.doFilter()");
        assertEquals(token, authentication.getCredentials());
        assertNull(authentication.getPrincipal(), "Principal debe ser null");
        assertTrue(authentication.getAuthorities().isEmpty(),
                "No debe tener authorities — la auth real la hace el Gateway");

        // Después del filtro, el contexto debe estar limpio
        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "El contexto debe estar limpio después de doFilterInternal");
    }

    @Test
    void doFilterInternal_conBearerToken_conPrincipalNull_siempreEsNull() throws Exception {
        // Given — verifica que principal siempre se setea como null intencionalmente
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer token-123");

        AtomicReference<Authentication> authRef = new AtomicReference<>();
        FilterChain chain = (req, res) -> authRef.set(
                SecurityContextHolder.getContext().getAuthentication());

        // When
        filter.doFilterInternal(request, response, chain);

        // Then
        Authentication auth = authRef.get();
        assertNotNull(auth);
        assertNull(auth.getPrincipal(), "Principal debe ser null por diseño");
        assertEquals("token-123", auth.getCredentials());
    }

    // =========================================================
    // Casos sin header Authorization
    // =========================================================

    @Test
    void doFilterInternal_sinHeaderAuthorization_noDebeSetearAuthentication() throws Exception {
        // Given — request sin header Authorization
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("Authorization")).thenReturn(null);

        AtomicReference<Authentication> authRef = new AtomicReference<>();
        FilterChain chain = (req, res) -> authRef.set(
                SecurityContextHolder.getContext().getAuthentication());

        // When
        filter.doFilterInternal(request, response, chain);

        // Then — dentro del chain, el contexto debe estar vacío
        assertNull(authRef.get(),
                "No debe haber authentication si no hay header Authorization");
    }

    // =========================================================
    // Casos borde — header mal formado
    // =========================================================

    @Test
    void doFilterInternal_conHeaderSinBearer_noDebeSetearAuthentication() throws Exception {
        // Given — header con "Basic" en vez de "Bearer "
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("Authorization")).thenReturn("Basic base64credenciales");

        AtomicReference<Authentication> authRef = new AtomicReference<>();
        FilterChain chain = (req, res) -> authRef.set(
                SecurityContextHolder.getContext().getAuthentication());

        // When
        filter.doFilterInternal(request, response, chain);

        // Then
        assertNull(authRef.get(), "No debe procesar headers que no sean Bearer");
    }

    @Test
    void doFilterInternal_conHeaderBearerMinuscula_noDebeSetearAuthentication() throws Exception {
        // Given — "bearer " en minúsculas (case-sensitive)
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("Authorization")).thenReturn("bearer token-minuscula");

        AtomicReference<Authentication> authRef = new AtomicReference<>();
        FilterChain chain = (req, res) -> authRef.set(
                SecurityContextHolder.getContext().getAuthentication());

        // When
        filter.doFilterInternal(request, response, chain);

        // Then — startsWith("Bearer ") falla con minúsculas
        assertNull(authRef.get(), "bearer en minúscula no debe ser procesado");
    }

    // =========================================================
    // Caso borde — token vacío después de "Bearer "
    // =========================================================

    @Test
    void doFilterInternal_conBearerTokenVacio_seteaTokenVacio() throws Exception {
        // Given — "Bearer " seguido de nada
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer ");

        AtomicReference<Authentication> authRef = new AtomicReference<>();
        FilterChain chain = (req, res) -> authRef.set(
                SecurityContextHolder.getContext().getAuthentication());

        // When
        filter.doFilterInternal(request, response, chain);

        // Then — el token vacío se almacena (el interceptor lo filtra con isBlank())
        Authentication auth = authRef.get();
        assertNotNull(auth, "Debe setear authentication incluso con token vacío");
        assertEquals("", auth.getCredentials(),
                "El token vacío es el resultado de substring(7) sobre 'Bearer '");
    }

    // =========================================================
    // Verificación de finally — clearContext() siempre se ejecuta
    // =========================================================

    @Test
    void doFilterInternal_cuandoChainLanzaExcepcion_limpiaContexto() throws Exception {
        // Given — chain.doFilter lanza excepción
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader("Authorization")).thenReturn("Bearer token-para-error");
        doThrow(new RuntimeException("Error en filtro posterior"))
                .when(chain).doFilter(request, response);

        // When & Then — la excepción se propaga
        assertThrows(RuntimeException.class,
                () -> filter.doFilterInternal(request, response, chain));

        // El contexto debe estar limpio (finally se ejecutó aunque chain lanzó excepción)
        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "El contexto debe limpiarse incluso si chain lanza excepción");
    }

    @Test
    void doFilterInternal_sinHeader_yChainLanzaExcepcion_limpiaContexto() throws Exception {
        // Given — sin header + chain lanza excepción
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader("Authorization")).thenReturn(null);
        doThrow(new RuntimeException("Error en filtro"))
                .when(chain).doFilter(request, response);

        // When & Then
        assertThrows(RuntimeException.class,
                () -> filter.doFilterInternal(request, response, chain));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    // =========================================================
    // Verificación de que chain.doFilter se llama siempre
    // =========================================================

    @Test
    void doFilterInternal_conTokenValido_chainDoFilterSeEjecuta() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader("Authorization")).thenReturn("Bearer token-valido");

        filter.doFilterInternal(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void doFilterInternal_sinHeader_chainDoFilterSeEjecuta() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void doFilterInternal_conTokenVacio_chainDoFilterSeEjecuta() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader("Authorization")).thenReturn("Bearer ");

        filter.doFilterInternal(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
    }

    // =========================================================
    // Aislamiento entre requests — el finally limpia el contexto
    // =========================================================

    @Test
    void doFilterInternal_llamadasConsecutivas_noFiltranTokenEntreRequests() throws Exception {
        // Given — dos requests consecutivos
        HttpServletRequest request1 = mock(HttpServletRequest.class);
        HttpServletRequest request2 = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        when(request1.getHeader("Authorization")).thenReturn("Bearer token-del-primer-request");
        when(request2.getHeader("Authorization")).thenReturn(null);

        // Capturar authentication del primer y segundo request
        AtomicReference<Authentication> authPrimero = new AtomicReference<>();
        AtomicReference<Authentication> authSegundo = new AtomicReference<>();

        FilterChain chain1 = (req, res) -> authPrimero.set(
                SecurityContextHolder.getContext().getAuthentication());
        FilterChain chain2 = (req, res) -> authSegundo.set(
                SecurityContextHolder.getContext().getAuthentication());

        // When — primer request con token
        filter.doFilterInternal(request1, response, chain1);

        // Verificar que dentro del primer chain el token está presente
        Authentication auth1 = authPrimero.get();
        assertNotNull(auth1);
        assertEquals("token-del-primer-request", auth1.getCredentials());

        // Después del primer filtro, el contexto se limpió en finally
        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "El finally limpió el contexto después del primer request");

        // When — segundo request sin token
        filter.doFilterInternal(request2, response, chain2);

        // Then — el segundo request no debe tener token del primero
        assertNull(authSegundo.get(),
                "No debe filtrarse el token del primer request al segundo");
    }

    // =========================================================
    // Verificación de que el filtro no interfiere con el response
    // =========================================================

    @Test
    void doFilterInternal_conBearerToken_noModificaResponse() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader("Authorization")).thenReturn("Bearer token-valido");

        filter.doFilterInternal(request, response, chain);

        // El filtro nunca escribe en el response — solo propaga el chain
        verify(response, never()).sendError(anyInt());
        verify(response, never()).setStatus(anyInt());
        verify(response, never()).getWriter();
        verify(response, never()).getOutputStream();
    }
}
