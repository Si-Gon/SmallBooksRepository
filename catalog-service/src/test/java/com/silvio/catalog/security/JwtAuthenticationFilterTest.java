package com.silvio.catalog.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests del JwtAuthenticationFilter.
 *
 * El filtro lee los headers X-User-Roles y X-User-Id propagados por el API Gateway
 * tras validar el JWT, y construye la autenticaci�n en SecurityContextHolder.
 * Se prueba de forma aislada sin cargar el contexto de Spring.
 *
 * NOTA: El filtro ejecuta SecurityContextHolder.clearContext() en el finally,
 * por lo que la autenticaci�n se captura dentro del callback de chain.doFilter
 * (antes de que el finally la limpie).
 */
class JwtAuthenticationFilterTest {

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void conHeaderAdmin_debeSetAuthoritiesConROLE_ADMIN() throws ServletException, IOException {
        // Given — header con rol de administrador
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Roles", "ROLE_ADMIN");
        request.addHeader("X-User-Id", "admin@test.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        // Captura la autenticaci�n en el momento que chain.doFilter es invocado
        AtomicReference<Authentication> capturedAuth = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedAuth.set(SecurityContextHolder.getContext().getAuthentication());
            return null;
        }).when(chain).doFilter(any(), any());

        // When
        filter.doFilterInternal(request, response, chain);

        // Then — debe tener la autoridad ROLE_ADMIN y principal correcto
        Authentication auth = capturedAuth.get();
        assertNotNull(auth, "La autenticaci�n debe estar establecida");
        assertEquals("admin@test.com", auth.getName(), "El principal debe ser el X-User-Id");

        List<SimpleGrantedAuthority> authorities = (List<SimpleGrantedAuthority>) auth.getAuthorities();
        assertEquals(1, authorities.size(), "Debe tener exactamente 1 autoridad");
        assertTrue(authorities.contains(new SimpleGrantedAuthority("ROLE_ADMIN")),
                "Debe contener ROLE_ADMIN");

        verify(chain).doFilter(request, response);
    }

    @Test
    void conHeaderUser_debeSetAuthoritiesConROLE_USER() throws ServletException, IOException {
        // Given — header con rol de usuario
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Roles", "ROLE_USER");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        AtomicReference<Authentication> capturedAuth = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedAuth.set(SecurityContextHolder.getContext().getAuthentication());
            return null;
        }).when(chain).doFilter(any(), any());

        // When
        filter.doFilterInternal(request, response, chain);

        // Then — debe tener la autoridad ROLE_USER
        Authentication auth = capturedAuth.get();
        assertNotNull(auth, "La autenticaci�n debe estar establecida");

        List<SimpleGrantedAuthority> authorities = (List<SimpleGrantedAuthority>) auth.getAuthorities();
        assertEquals(1, authorities.size());
        assertTrue(authorities.contains(new SimpleGrantedAuthority("ROLE_USER")));

        verify(chain).doFilter(request, response);
    }

    @Test
    void sinHeader_debeSetAuthoritiesVacio() throws ServletException, IOException {
        // Given — sin header X-User-Roles
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        AtomicReference<Authentication> capturedAuth = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedAuth.set(SecurityContextHolder.getContext().getAuthentication());
            return null;
        }).when(chain).doFilter(any(), any());

        // When
        filter.doFilterInternal(request, response, chain);

        // Then — authorities vac�as y principal cadena vac�a (Spring Security devuelve "" cuando principal es null)
        Authentication auth = capturedAuth.get();
        assertNotNull(auth, "La autenticaci�n debe estar establecida incluso sin roles");
        assertTrue(auth.getAuthorities().isEmpty(), "Las autoridades deben estar vac�as");
        assertEquals("", auth.getName(), "El principal debe ser cadena vac�a si no hay X-User-Id");

        verify(chain).doFilter(request, response);
    }

    @Test
    void conHeaderBlanco_debeSetAuthoritiesVacio() throws ServletException, IOException {
        // Given — header X-User-Roles vac�o (blank)
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Roles", "");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        AtomicReference<Authentication> capturedAuth = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedAuth.set(SecurityContextHolder.getContext().getAuthentication());
            return null;
        }).when(chain).doFilter(any(), any());

        // When
        filter.doFilterInternal(request, response, chain);

        // Then — authorities vac�as
        Authentication auth = capturedAuth.get();
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().isEmpty(), "Header blank debe producir authorities vac�as");

        verify(chain).doFilter(request, response);
    }

    // =========================================================
    // Casos borde — m�ltiples roles y formatos del header
    // =========================================================

    @Test
    void conMultiplesRolesSeparadosPorComa_debeSetAuthoritiesConTodos() throws ServletException, IOException {
        // Given — m�ltiples roles separados por coma
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Roles", "ROLE_USER,ROLE_ADMIN");
        request.addHeader("X-User-Id", "silvio");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        AtomicReference<Authentication> capturedAuth = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedAuth.set(SecurityContextHolder.getContext().getAuthentication());
            return null;
        }).when(chain).doFilter(any(), any());

        // When
        filter.doFilterInternal(request, response, chain);

        // Then — ambas autoridades presentes
        Authentication auth = capturedAuth.get();
        assertNotNull(auth);
        assertEquals("silvio", auth.getName());

        List<SimpleGrantedAuthority> authorities = (List<SimpleGrantedAuthority>) auth.getAuthorities();
        assertEquals(2, authorities.size());
        assertTrue(authorities.contains(new SimpleGrantedAuthority("ROLE_USER")));
        assertTrue(authorities.contains(new SimpleGrantedAuthority("ROLE_ADMIN")));

        verify(chain).doFilter(request, response);
    }

    @Test
    void conRolesConEspaciosExtra_debeTrimYSetAuthorities() throws ServletException, IOException {
        // Given — roles con espacios alrededor de comas y valores
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Roles", " ROLE_ADMIN , ROLE_USER ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        AtomicReference<Authentication> capturedAuth = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedAuth.set(SecurityContextHolder.getContext().getAuthentication());
            return null;
        }).when(chain).doFilter(any(), any());

        // When
        filter.doFilterInternal(request, response, chain);

        // Then — ambas autoridades presentes a pesar de espacios extra
        Authentication auth = capturedAuth.get();
        assertNotNull(auth);

        List<SimpleGrantedAuthority> authorities = (List<SimpleGrantedAuthority>) auth.getAuthorities();
        assertEquals(2, authorities.size());
        assertTrue(authorities.contains(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertTrue(authorities.contains(new SimpleGrantedAuthority("ROLE_USER")));

        verify(chain).doFilter(request, response);
    }

    @Test
    void conRolesConComasVacias_debeFiltrarVacios() throws ServletException, IOException {
        // Given — header con comas vac�as: "ROLE_USER,,ROLE_ADMIN,"
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Roles", "ROLE_USER,,ROLE_ADMIN,");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        AtomicReference<Authentication> capturedAuth = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedAuth.set(SecurityContextHolder.getContext().getAuthentication());
            return null;
        }).when(chain).doFilter(any(), any());

        // When
        filter.doFilterInternal(request, response, chain);

        // Then — solo 2 autoridades, las partes vac�as filtradas
        Authentication auth = capturedAuth.get();
        assertNotNull(auth);

        List<SimpleGrantedAuthority> authorities = (List<SimpleGrantedAuthority>) auth.getAuthorities();
        assertEquals(2, authorities.size());
        assertTrue(authorities.contains(new SimpleGrantedAuthority("ROLE_USER")));
        assertTrue(authorities.contains(new SimpleGrantedAuthority("ROLE_ADMIN")));

        verify(chain).doFilter(request, response);
    }

    @Test
    void debeLimpiarSecurityContextAlFinalizar() throws ServletException, IOException {
        // Given — petici�n con roles
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Roles", "ROLE_ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        // When
        filter.doFilterInternal(request, response, chain);

        // Then — el contexto debe estar limpio despu�s del finally
        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "SecurityContext debe estar limpio tras la ejecuci�n del filtro");
    }
}
