package com.silvio.elending.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests del JwtAuthenticationFilter.
 *
 * Verifica que el filtro extrae correctamente los headers propagados por el Gateway:
 * - X-User-Id → principal (identidad del usuario)
 * - Authorization Bearer → credentials (token JWT para Feign)
 * - X-User-Roles → authorities (roles para hasRole())
 *
 * El filtro SIEMPRE setea Authentication en SecurityContextHolder (patrón catalog-service),
 * incluso si los headers están ausentes — con valores null/vacíos según corresponda.
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
    // Casos felices — todos los headers presentes
    // =========================================================

    @Test
    void doFilterInternal_conTodosLosHeaders_seteaAuthenticationCompleta() throws Exception {
        // Given — request con todos los headers propagados por el Gateway
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        String token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c3VhcmlvMSJ9.firma";
        when(request.getHeader("X-User-Id")).thenReturn("usuario1");
        when(request.getHeader("X-User-Roles")).thenReturn("ROLE_USER");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        // Capturar el estado dentro del chain (antes del finally)
        AtomicReference<Authentication> authRef = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            authRef.set(auth);
        };

        // When
        filter.doFilterInternal(request, response, chain);

        // Then — authentication completa con principal, credentials y authorities
        Authentication authentication = authRef.get();
        assertNotNull(authentication, "Debe haber authentication durante chain.doFilter()");
        assertEquals("usuario1", authentication.getPrincipal(), "Principal debe ser X-User-Id");
        assertEquals(token, authentication.getCredentials(), "Credentials debe ser el token JWT para Feign");
        assertEquals(1, authentication.getAuthorities().size());
        assertTrue(authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_USER")));

        // Después del filtro, el contexto debe estar limpio
        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "El contexto debe estar limpio después de doFilterInternal");
    }

    @Test
    void doFilterInternal_conMultiplesRoles_seteaTodasLasAuthorities() throws Exception {
        // Given — usuario con múltiples roles (admin + user)
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("X-User-Id")).thenReturn("admin1");
        when(request.getHeader("X-User-Roles")).thenReturn("ROLE_ADMIN,ROLE_USER");
        when(request.getHeader("Authorization")).thenReturn("Bearer token-admin");

        AtomicReference<Authentication> authRef = new AtomicReference<>();
        FilterChain chain = (req, res) -> authRef.set(
                SecurityContextHolder.getContext().getAuthentication());

        // When
        filter.doFilterInternal(request, response, chain);

        // Then
        Authentication auth = authRef.get();
        assertNotNull(auth);
        assertEquals("admin1", auth.getPrincipal());
        assertEquals("token-admin", auth.getCredentials());
        assertEquals(2, auth.getAuthorities().size());
        assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_USER")));
    }

    // =========================================================
    // Casos sin headers — authentication se setea con valores nulos/vacíos
    // =========================================================

    @Test
    void doFilterInternal_sinHeaders_seteaAuthenticationConValoresNulos() throws Exception {
        // Given — request sin ningún header de seguridad
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("X-User-Id")).thenReturn(null);
        when(request.getHeader("X-User-Roles")).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn(null);

        AtomicReference<Authentication> authRef = new AtomicReference<>();
        FilterChain chain = (req, res) -> authRef.set(
                SecurityContextHolder.getContext().getAuthentication());

        // When
        filter.doFilterInternal(request, response, chain);

        // Then — authentication siempre se setea (patrón catalog-service)
        Authentication auth = authRef.get();
        assertNotNull(auth, "Authentication siempre se setea (patrón catalog-service)");
        assertNull(auth.getPrincipal(), "Principal es null sin X-User-Id");
        assertNull(auth.getCredentials(), "Credentials es null sin Authorization");
        assertTrue(auth.getAuthorities().isEmpty(), "Authorities vacío sin X-User-Roles");
    }

    @Test
    void doFilterInternal_conSoloXUserId_seteaPrincipalSinAuthorities() throws Exception {
        // Given — solo X-User-Id, sin roles ni token
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("X-User-Id")).thenReturn("usuario1");
        when(request.getHeader("X-User-Roles")).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn(null);

        AtomicReference<Authentication> authRef = new AtomicReference<>();
        FilterChain chain = (req, res) -> authRef.set(
                SecurityContextHolder.getContext().getAuthentication());

        // When
        filter.doFilterInternal(request, response, chain);

        // Then
        Authentication auth = authRef.get();
        assertNotNull(auth);
        assertEquals("usuario1", auth.getPrincipal());
        assertNull(auth.getCredentials());
        assertTrue(auth.getAuthorities().isEmpty());
    }

    @Test
    void doFilterInternal_conSoloXUserRoles_seteaAuthoritiesSinPrincipal() throws Exception {
        // Given — solo X-User-Roles, sin userId ni token
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("X-User-Id")).thenReturn(null);
        when(request.getHeader("X-User-Roles")).thenReturn("ROLE_USER");
        when(request.getHeader("Authorization")).thenReturn(null);

        AtomicReference<Authentication> authRef = new AtomicReference<>();
        FilterChain chain = (req, res) -> authRef.set(
                SecurityContextHolder.getContext().getAuthentication());

        // When
        filter.doFilterInternal(request, response, chain);

        // Then
        Authentication auth = authRef.get();
        assertNotNull(auth);
        assertNull(auth.getPrincipal());
        assertNull(auth.getCredentials());
        assertEquals(1, auth.getAuthorities().size());
        assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_USER")));
    }

    // =========================================================
    // Casos borde — header Authorization mal formado
    // =========================================================

    @Test
    void doFilterInternal_conHeaderSinBearer_noExtraeToken() throws Exception {
        // Given — header con "Basic" en vez de "Bearer "
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("X-User-Id")).thenReturn("usuario1");
        when(request.getHeader("X-User-Roles")).thenReturn("ROLE_USER");
        when(request.getHeader("Authorization")).thenReturn("Basic base64credenciales");

        AtomicReference<Authentication> authRef = new AtomicReference<>();
        FilterChain chain = (req, res) -> authRef.set(
                SecurityContextHolder.getContext().getAuthentication());

        // When
        filter.doFilterInternal(request, response, chain);

        // Then — token no se extrae, pero principal y authorities sí
        Authentication auth = authRef.get();
        assertNotNull(auth);
        assertEquals("usuario1", auth.getPrincipal());
        assertNull(auth.getCredentials(), "Token no se extrae de headers que no son Bearer");
        assertFalse(auth.getAuthorities().isEmpty());
    }

    @Test
    void doFilterInternal_conHeaderBearerMinuscula_noExtraeToken() throws Exception {
        // Given — "bearer " en minúsculas (case-sensitive)
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("X-User-Id")).thenReturn(null);
        when(request.getHeader("X-User-Roles")).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn("bearer token-minuscula");

        AtomicReference<Authentication> authRef = new AtomicReference<>();
        FilterChain chain = (req, res) -> authRef.set(
                SecurityContextHolder.getContext().getAuthentication());

        // When
        filter.doFilterInternal(request, response, chain);

        // Then — bearer en minúscula no es procesado (startsWith es case-sensitive)
        Authentication auth = authRef.get();
        assertNotNull(auth);
        assertNull(auth.getCredentials(), "bearer en minúscula no debe ser procesado");
    }

    // =========================================================
    // Caso borde — token vacío después de "Bearer "
    // =========================================================

    @Test
    void doFilterInternal_conBearerTokenVacio_seteaTokenVacio() throws Exception {
        // Given — "Bearer " seguido de nada
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("X-User-Id")).thenReturn("usuario1");
        when(request.getHeader("X-User-Roles")).thenReturn("ROLE_USER");
        when(request.getHeader("Authorization")).thenReturn("Bearer ");

        AtomicReference<Authentication> authRef = new AtomicReference<>();
        FilterChain chain = (req, res) -> authRef.set(
                SecurityContextHolder.getContext().getAuthentication());

        // When
        filter.doFilterInternal(request, response, chain);

        // Then — el token vacío se almacena (FeignRequestInterceptor lo filtra con isBlank())
        Authentication auth = authRef.get();
        assertNotNull(auth);
        assertEquals("", auth.getCredentials(),
                "El token vacío es el resultado de substring(7) sobre 'Bearer '");
    }

    // =========================================================
    // Caso borde — X-User-Roles vacío o con espacios
    // =========================================================

    @Test
    void doFilterInternal_conXUserRolesVacio_seteaAuthoritiesVacias() throws Exception {
        // Given — X-User-Roles vacío
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("X-User-Id")).thenReturn("usuario1");
        when(request.getHeader("X-User-Roles")).thenReturn("");
        when(request.getHeader("Authorization")).thenReturn("Bearer token");

        AtomicReference<Authentication> authRef = new AtomicReference<>();
        FilterChain chain = (req, res) -> authRef.set(
                SecurityContextHolder.getContext().getAuthentication());

        // When
        filter.doFilterInternal(request, response, chain);

        // Then
        Authentication auth = authRef.get();
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().isEmpty(), "Roles vacío → authorities vacías");
    }

    @Test
    void doFilterInternal_conXUserRolesConEspacios_parseaCorrectamente() throws Exception {
        // Given — roles con espacios intermedios
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("X-User-Id")).thenReturn("usuario1");
        when(request.getHeader("X-User-Roles")).thenReturn(" ROLE_ADMIN , ROLE_USER ");
        when(request.getHeader("Authorization")).thenReturn("Bearer token");

        AtomicReference<Authentication> authRef = new AtomicReference<>();
        FilterChain chain = (req, res) -> authRef.set(
                SecurityContextHolder.getContext().getAuthentication());

        // When
        filter.doFilterInternal(request, response, chain);

        // Then — los espacios se recortan con trim()
        Authentication auth = authRef.get();
        assertNotNull(auth);
        assertEquals(2, auth.getAuthorities().size());
        assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_USER")));
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

        when(request.getHeader("X-User-Id")).thenReturn("usuario1");
        when(request.getHeader("X-User-Roles")).thenReturn("ROLE_USER");
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
    void doFilterInternal_sinHeaders_yChainLanzaExcepcion_limpiaContexto() throws Exception {
        // Given — sin headers + chain lanza excepción
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader("X-User-Id")).thenReturn(null);
        when(request.getHeader("X-User-Roles")).thenReturn(null);
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
    void doFilterInternal_conHeadersCompletos_chainDoFilterSeEjecuta() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader("X-User-Id")).thenReturn("usuario1");
        when(request.getHeader("X-User-Roles")).thenReturn("ROLE_USER");
        when(request.getHeader("Authorization")).thenReturn("Bearer token-valido");

        filter.doFilterInternal(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void doFilterInternal_sinHeaders_chainDoFilterSeEjecuta() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader("X-User-Id")).thenReturn(null);
        when(request.getHeader("X-User-Roles")).thenReturn(null);
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
    }

    @Test
    void doFilterInternal_conTokenVacio_chainDoFilterSeEjecuta() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader("X-User-Id")).thenReturn("usuario1");
        when(request.getHeader("X-User-Roles")).thenReturn("ROLE_USER");
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

        when(request1.getHeader("X-User-Id")).thenReturn("usuario1");
        when(request1.getHeader("X-User-Roles")).thenReturn("ROLE_USER");
        when(request1.getHeader("Authorization")).thenReturn("Bearer token-del-primer-request");

        when(request2.getHeader("X-User-Id")).thenReturn(null);
        when(request2.getHeader("X-User-Roles")).thenReturn(null);
        when(request2.getHeader("Authorization")).thenReturn(null);

        AtomicReference<Authentication> authPrimero = new AtomicReference<>();
        AtomicReference<Authentication> authSegundo = new AtomicReference<>();

        FilterChain chain1 = (req, res) -> authPrimero.set(
                SecurityContextHolder.getContext().getAuthentication());
        FilterChain chain2 = (req, res) -> authSegundo.set(
                SecurityContextHolder.getContext().getAuthentication());

        // When — primer request con todos los headers
        filter.doFilterInternal(request1, response, chain1);

        // Verificar que dentro del primer chain el authentication está completo
        Authentication auth1 = authPrimero.get();
        assertNotNull(auth1);
        assertEquals("usuario1", auth1.getPrincipal());
        assertEquals("token-del-primer-request", auth1.getCredentials());

        // Después del primer filtro, el contexto se limpió en finally
        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "El finally limpió el contexto después del primer request");

        // When — segundo request sin headers
        filter.doFilterInternal(request2, response, chain2);

        // Then — el segundo request tiene auth con valores nulos (no del primero)
        Authentication auth2 = authSegundo.get();
        assertNotNull(auth2, "Authentication siempre se setea");
        assertNull(auth2.getPrincipal(), "No debe filtrarse el principal del primer request");
        assertNull(auth2.getCredentials(), "No debe filtrarse el token del primer request");
        assertTrue(auth2.getAuthorities().isEmpty(), "No deben filtrarse las authorities del primer request");
    }

    // =========================================================
    // Verificación de que el filtro no interfiere con el response
    // =========================================================

    @Test
    void doFilterInternal_conHeadersCompletos_noModificaResponse() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader("X-User-Id")).thenReturn("usuario1");
        when(request.getHeader("X-User-Roles")).thenReturn("ROLE_USER");
        when(request.getHeader("Authorization")).thenReturn("Bearer token-valido");

        filter.doFilterInternal(request, response, chain);

        // El filtro nunca escribe en el response — solo propaga el chain
        verify(response, never()).sendError(anyInt());
        verify(response, never()).setStatus(anyInt());
        verify(response, never()).getWriter();
        verify(response, never()).getOutputStream();
    }
}
