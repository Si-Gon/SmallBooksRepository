package com.silvio.identity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_headerXUserRolesAusente_noLanzaNPE() throws ServletException, IOException {
        // Given — token válido pero extractRoles retorna null (sin claim "roles")
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer fake.jwt.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(jwtUtil.extractUsername("fake.jwt.token")).thenReturn("silvio");
        when(jwtUtil.extractRoles("fake.jwt.token")).thenReturn(null);

        // When & Then — no debe lanzar NPE
        assertDoesNotThrow(() ->
                jwtAuthenticationFilter.doFilterInternal(request, response, filterChain));

        // Verificar que la autenticación se estableció con authorities vacías
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().isEmpty());

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_headerVacio_debeRetornarAuthoritiesVacias() throws ServletException, IOException {
        // Given — token válido pero roles vacío (string blank)
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer fake.jwt.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(jwtUtil.extractUsername("fake.jwt.token")).thenReturn("silvio");
        when(jwtUtil.extractRoles("fake.jwt.token")).thenReturn("   ");

        // When & Then
        assertDoesNotThrow(() ->
                jwtAuthenticationFilter.doFilterInternal(request, response, filterChain));

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertTrue(auth.getAuthorities().isEmpty());

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_headerValido_debeGenerar2Authorities() throws ServletException, IOException {
        // Given — token con roles ROLE_USER,ROLE_ADMIN
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer fake.jwt.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(jwtUtil.extractUsername("fake.jwt.token")).thenReturn("silvio");
        when(jwtUtil.extractRoles("fake.jwt.token")).thenReturn("ROLE_USER,ROLE_ADMIN");

        // When & Then
        assertDoesNotThrow(() ->
                jwtAuthenticationFilter.doFilterInternal(request, response, filterChain));

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        List<SimpleGrantedAuthority> authorities = (List<SimpleGrantedAuthority>) auth.getAuthorities();
        assertEquals(2, authorities.size());
        assertTrue(authorities.contains(new SimpleGrantedAuthority("ROLE_USER")));
        assertTrue(authorities.contains(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertEquals("silvio", auth.getName());

        verify(filterChain).doFilter(request, response);
    }

    // =========================================================
    // Casos borde — roles con espacios extra
    // =========================================================

    @Test
    void doFilterInternal_rolesConEspaciosExtra_debeTrimCorrectamente() throws ServletException, IOException {
        // Given — roles con espacios extra alrededor de cada rol y comas
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer fake.jwt.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(jwtUtil.extractUsername("fake.jwt.token")).thenReturn("silvio");
        when(jwtUtil.extractRoles("fake.jwt.token")).thenReturn(" ROLE_USER , ROLE_ADMIN ");

        // When & Then
        assertDoesNotThrow(() ->
                jwtAuthenticationFilter.doFilterInternal(request, response, filterChain));

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        List<SimpleGrantedAuthority> authorities = (List<SimpleGrantedAuthority>) auth.getAuthorities();
        assertEquals(2, authorities.size());
        assertTrue(authorities.contains(new SimpleGrantedAuthority("ROLE_USER")));
        assertTrue(authorities.contains(new SimpleGrantedAuthority("ROLE_ADMIN")));

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_rolesConComasVacias_debeFiltrarVacios() throws ServletException, IOException {
        // Given — roles con comas vacías: "ROLE_USER,,ROLE_ADMIN,"
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer fake.jwt.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(jwtUtil.extractUsername("fake.jwt.token")).thenReturn("silvio");
        when(jwtUtil.extractRoles("fake.jwt.token")).thenReturn("ROLE_USER,,ROLE_ADMIN,");

        // When & Then — las partes vacías deben ser filtradas
        assertDoesNotThrow(() ->
                jwtAuthenticationFilter.doFilterInternal(request, response, filterChain));

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        List<SimpleGrantedAuthority> authorities = (List<SimpleGrantedAuthority>) auth.getAuthorities();
        assertEquals(2, authorities.size());
        assertTrue(authorities.contains(new SimpleGrantedAuthority("ROLE_USER")));
        assertTrue(authorities.contains(new SimpleGrantedAuthority("ROLE_ADMIN")));

        verify(filterChain).doFilter(request, response);
    }

    // =========================================================
    // Authorization header sin prefijo Bearer
    // =========================================================

    @Test
    void doFilterInternal_authSinBearerPrefix_noEstableceAutenticacion() throws ServletException, IOException {
        // Given — header Authorization sin prefijo "Bearer "
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "fake.jwt.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then — no se debe establecer autenticación
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "No debe haber autenticación sin prefijo Bearer");

        // El filtro debe continuar con la cadena
        verify(filterChain).doFilter(request, response);
        // JwtUtil NO debe ser invocado
        verify(jwtUtil, never()).extractUsername(any());
        verify(jwtUtil, never()).extractRoles(any());
    }

    @Test
    void doFilterInternal_authHeaderNulo_noEstableceAutenticacion() throws ServletException, IOException {
        // Given — sin header Authorization
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "No debe haber autenticación sin header Authorization");

        verify(filterChain).doFilter(request, response);
        verify(jwtUtil, never()).extractUsername(any());
        verify(jwtUtil, never()).extractRoles(any());
    }

    @Test
    void doFilterInternal_tokenInvalido_debeRetornar401() throws ServletException, IOException {
        // Given — token JWT inválido (extractUsername lanza excepción)
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token.invalido");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(jwtUtil.extractUsername("token.invalido")).thenThrow(new RuntimeException("Token inválido"));

        // When
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // Then — debe retornar 401 y NO continuar con la cadena
        assertEquals(401, response.getStatus());
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilterInternal_unSoloRole_debeGenerar1Authority() throws ServletException, IOException {
        // Given — token con un solo rol
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer fake.jwt.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(jwtUtil.extractUsername("fake.jwt.token")).thenReturn("silvio");
        when(jwtUtil.extractRoles("fake.jwt.token")).thenReturn("ROLE_USER");

        // When & Then
        assertDoesNotThrow(() ->
                jwtAuthenticationFilter.doFilterInternal(request, response, filterChain));

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        List<SimpleGrantedAuthority> authorities = (List<SimpleGrantedAuthority>) auth.getAuthorities();
        assertEquals(1, authorities.size());
        assertTrue(authorities.contains(new SimpleGrantedAuthority("ROLE_USER")));

        verify(filterChain).doFilter(request, response);
    }
}
