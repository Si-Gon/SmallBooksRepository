package com.silvio.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvio.identity.config.SecurityConfig;
import com.silvio.identity.dto.*;
import com.silvio.identity.security.JwtAuthenticationFilter;
import com.silvio.identity.security.JwtUtil;
import com.silvio.identity.exception.TokenExpiradoException;
import com.silvio.identity.exception.TokenInvalidoException;
import com.silvio.identity.exception.UsuarioDuplicadoException;
import com.silvio.identity.exception.UsuarioNotFoundException;
import com.silvio.identity.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests del AuthController.
 *
 * Estrategia para @WebMvcTest + Spring Security 6:
 *
 * 1. excludeFilters excluye SecurityConfig y JwtAuthenticationFilter del contexto
 *    → Spring no carga la config de seguridad real ni el filtro JWT
 *
 * 2. @TestConfiguration dentro del test provee un SecurityFilterChain que
 *    desactiva CSRF y permite todas las peticiones
 *    → Reemplaza el vacío que dejó excluir SecurityConfig
 *
 * 3. .with(csrf()) en los POST es necesario igualmente como buena práctica,
 *    aunque el TestSecurityConfig ya deshabilita CSRF — es defensivo
 *
 * ¿Por qué 401 sin el TestSecurityConfig?
 * Al excluir SecurityConfig, Spring Boot aplica su auto-configuración de
 * seguridad por defecto: httpBasic con todas las rutas autenticadas.
 * El TestSecurityConfig reemplaza eso con "permitAll".
 */
@WebMvcTest(
    value = AuthController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = { SecurityConfig.class, JwtAuthenticationFilter.class }
    )
)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private AuthenticationManager authenticationManager;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Configuración de seguridad para tests.
     *
     * Al estar dentro de la clase de test con @TestConfiguration,
     * Spring la detecta automáticamente como parte del contexto de @WebMvcTest
     * sin necesidad de @Import. Desactiva CSRF y permite todo.
     */
    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    private UserDetails userDetailsTest(String username) {
        return new User(username, "$2a$10$hash",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    // =========================================================
    // POST /auth/login
    // =========================================================

    @Test
    void login_exitoso_debeRetornar200ConToken() throws Exception {
        AuthRequest request = new AuthRequest();
        request.setUsername("silvio");
        request.setPassword("password123");

        UserDetails userDetails = userDetailsTest("silvio");
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());

        when(authenticationManager.authenticate(any())).thenReturn(authToken);
        when(jwtUtil.generateAccessToken(any())).thenReturn("access.token.fake");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("refresh.token.fake");
        doNothing().when(userService).storeRefreshTokenHash(any(), any());

        mockMvc.perform(post("/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access.token.fake"))
                .andExpect(jsonPath("$.refreshToken").value("refresh.token.fake"));

        verify(authenticationManager).authenticate(any());
        verify(jwtUtil).generateAccessToken(any());
        verify(userService).storeRefreshTokenHash(eq("silvio"), any());
    }

    @Test
    void login_credencialesIncorrectas_debeRetornar401() throws Exception {
        AuthRequest request = new AuthRequest();
        request.setUsername("silvio");
        request.setPassword("wrongpassword");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Credenciales incorrectas"));

        mockMvc.perform(post("/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================
    // POST /auth/register
    // =========================================================

    @Test
    void register_exitoso_debeRetornar201() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("nuevousuario");
        request.setPassword("Password123!");
        request.setRoles(Set.of("ROLE_USER"));

        doNothing().when(userService).registerUser(any(), any(), any());

        mockMvc.perform(post("/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").exists());

        verify(userService).registerUser(eq("nuevousuario"), eq("Password123!"), any());
    }

    @Test
    void register_usernameYaExiste_debeRetornar409() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("existente");
        request.setPassword("Password123!");

        doThrow(new UsuarioDuplicadoException("existente"))
                .when(userService).registerUser(any(), any(), any());

        mockMvc.perform(post("/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // =========================================================
    // POST /auth/forgot-password
    // =========================================================

    @Test
    void forgotPassword_usuarioExistente_debeRetornar200ConToken() throws Exception {
        PasswordResetRequest request = new PasswordResetRequest();
        request.setUsername("silvio");

        when(userService.createPasswordResetToken("silvio")).thenReturn("reset-uuid-token");

        mockMvc.perform(post("/auth/forgot-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetToken").value("reset-uuid-token"));
    }

    // =========================================================
    // POST /auth/reset-password
    // =========================================================

    @Test
    void resetPassword_tokenValido_debeRetornar200() throws Exception {
        PasswordUpdateRequest request = new PasswordUpdateRequest();
        request.setToken("valid-reset-token");
        request.setNewPassword("NuevaPassword123!");

        doNothing().when(userService).resetPassword(any(), any());

        mockMvc.perform(post("/auth/reset-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(userService).resetPassword("valid-reset-token", "NuevaPassword123!");
    }

    @Test
    void resetPassword_tokenExpirado_debeRetornar401() throws Exception {
        PasswordUpdateRequest request = new PasswordUpdateRequest();
        request.setToken("expired-token");
        request.setNewPassword("NuevaPassword123!");

        // TokenExpiradoException → GlobalExceptionHandler devuelve 401
        doThrow(new TokenExpiradoException())
                .when(userService).resetPassword(any(), any());

        mockMvc.perform(post("/auth/reset-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================
    // POST /auth/change-password
    // =========================================================

    @Test
    void changePassword_exitoso_debeRetornar200() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("passwordActual");
        request.setNewPassword("NuevaPassword123!");

        when(jwtUtil.extractUsername("fake.jwt.token")).thenReturn("silvio");
        doNothing().when(userService).changePassword(any(), any(), any());

        mockMvc.perform(post("/auth/change-password").with(csrf())
                        .header("Authorization", "Bearer fake.jwt.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(jwtUtil).extractUsername("fake.jwt.token");
        verify(userService).changePassword(eq("silvio"), any(), any());
    }

    // =========================================================
    // POST /auth/refresh — Refresh Token Rotation
    // =========================================================

    @Test
    void refreshToken_exitoso_debeRetornar200ConNuevosTokens() throws Exception {
        // Given
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh.token.valido");

        UserDetails userDetails = userDetailsTest("silvio");

        when(jwtUtil.isTokenExpired("refresh.token.valido")).thenReturn(false);
        when(jwtUtil.extractTokenType("refresh.token.valido")).thenReturn("refresh");
        when(jwtUtil.extractUsername("refresh.token.valido")).thenReturn("silvio");
        when(userService.loadUserByUsername("silvio")).thenReturn(userDetails);
        when(jwtUtil.generateAccessToken(userDetails)).thenReturn("nuevo.access.token");
        when(jwtUtil.generateRefreshToken("silvio")).thenReturn("nuevo.refresh.token");
        doNothing().when(userService).rotateRefreshToken("refresh.token.valido", "nuevo.refresh.token");

        // When & Then
        mockMvc.perform(post("/auth/refresh").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("nuevo.access.token"))
                .andExpect(jsonPath("$.refreshToken").value("nuevo.refresh.token"))
                .andExpect(jsonPath("$.message").value(" Token refrescado exitosamente"))
                .andExpect(jsonPath("$.username").value("silvio"));

        verify(jwtUtil).isTokenExpired("refresh.token.valido");
        verify(jwtUtil).extractTokenType("refresh.token.valido");
        verify(userService).rotateRefreshToken("refresh.token.valido", "nuevo.refresh.token");
    }

    @Test
    void refreshToken_expirado_debeRetornar401() throws Exception {
        // Given
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh.token.expirado");

        // isTokenExpired retorna true — se verifica ANTES de cualquier otra validación
        when(jwtUtil.isTokenExpired("refresh.token.expirado")).thenReturn(true);

        // When & Then
        mockMvc.perform(post("/auth/refresh").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(" Refresh token expirado"));

        verify(jwtUtil).isTokenExpired("refresh.token.expirado");
        // No deben invocarse métodos posteriores si el token ya expiró
        verify(jwtUtil, never()).extractTokenType(any());
        verify(userService, never()).rotateRefreshToken(any(), any());
    }

    @Test
    void refreshToken_tokenYaRotado_debeRetornar401() throws Exception {
        // Given
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh.token.ya.rotado");

        UserDetails userDetails = userDetailsTest("silvio");

        // El token no está expirado y es de tipo refresh
        when(jwtUtil.isTokenExpired("refresh.token.ya.rotado")).thenReturn(false);
        when(jwtUtil.extractTokenType("refresh.token.ya.rotado")).thenReturn("refresh");
        when(jwtUtil.extractUsername("refresh.token.ya.rotado")).thenReturn("silvio");
        when(userService.loadUserByUsername("silvio")).thenReturn(userDetails);
        when(jwtUtil.generateAccessToken(userDetails)).thenReturn("nuevo.access.token");
        when(jwtUtil.generateRefreshToken("silvio")).thenReturn("nuevo.refresh.token");

        // rotateRefreshToken lanza excepción porque el token ya fue rotado (reuso detectado)
        doThrow(new TokenInvalidoException())
                .when(userService).rotateRefreshToken("refresh.token.ya.rotado", "nuevo.refresh.token");

        // When & Then
        mockMvc.perform(post("/auth/refresh").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        " Refresh token inválido o ya utilizado. Por favor inicia sesión nuevamente."));

        verify(jwtUtil).isTokenExpired("refresh.token.ya.rotado");
        verify(jwtUtil).extractTokenType("refresh.token.ya.rotado");
        verify(userService).rotateRefreshToken("refresh.token.ya.rotado", "nuevo.refresh.token");
    }

    @Test
    void refreshToken_tokenTipoAccess_debeRetornar401() throws Exception {
        // Given
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("access.token.falso");

        // Token no expirado pero es de tipo "access", no "refresh"
        when(jwtUtil.isTokenExpired("access.token.falso")).thenReturn(false);
        when(jwtUtil.extractTokenType("access.token.falso")).thenReturn("access");

        // When & Then
        mockMvc.perform(post("/auth/refresh").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(" Token inválido: no es un refresh token"));

        verify(jwtUtil).isTokenExpired("access.token.falso");
        verify(jwtUtil).extractTokenType("access.token.falso");
        // No debe llegar a rotar si el tipo es incorrecto
        verify(userService, never()).rotateRefreshToken(any(), any());
    }

    // =========================================================
    // POST /auth/forgot-password — casos borde
    // =========================================================

    @Test
    void forgotPassword_usuarioNoExistente_debeRetornar404() throws Exception {
        // Given
        PasswordResetRequest request = new PasswordResetRequest();
        request.setUsername("usuario_inexistente");

        // createPasswordResetToken lanza UsuarioNotFoundException si el usuario no existe
        doThrow(new UsuarioNotFoundException("usuario_inexistente"))
                .when(userService).createPasswordResetToken("usuario_inexistente");

        // When & Then
        mockMvc.perform(post("/auth/forgot-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());

        verify(userService).createPasswordResetToken("usuario_inexistente");
    }
}
