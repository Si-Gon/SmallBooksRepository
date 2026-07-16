package com.silvio.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvio.identity.config.SecurityConfig;
import com.silvio.identity.dto.*;
import com.silvio.identity.security.JwtAuthenticationFilter;
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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;



import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
 *
 * Patrón CSR: el controller delega toda la lógica de negocio a UserService.
 * Los tests verifican que el controller orquesta correctamente las llamadas
 * al servicio y construye las respuestas HTTP adecuadas.
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

    // =========================================================
    // POST /auth/login
    // =========================================================

    @Test
    void login_exitoso_debeRetornar200ConToken() throws Exception {
        AuthRequest request = new AuthRequest();
        request.setUsername("silvio");
        request.setPassword("password123");

        AuthResponse expectedResponse = new AuthResponse(
                "access.token.fake", "refresh.token.fake",
                " Login exitoso. Bienvenido silvio", "silvio");

        when(userService.login(any(AuthRequest.class))).thenReturn(expectedResponse);

        mockMvc.perform(post("/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access.token.fake"))
                .andExpect(jsonPath("$.refreshToken").value("refresh.token.fake"));

        verify(userService).login(any(AuthRequest.class));
    }

    @Test
    void login_credencialesIncorrectas_debeRetornar401() throws Exception {
        AuthRequest request = new AuthRequest();
        request.setUsername("silvio");
        request.setPassword("wrongpassword");

        when(userService.login(any(AuthRequest.class)))
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

        doNothing().when(userService).registerUser(any(), any());

        // Verificar que retorna Map<String, Object> con message, username y status
        mockMvc.perform(post("/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value(" Usuario 'nuevousuario' registrado exitosamente"))
                .andExpect(jsonPath("$.username").value("nuevousuario"))
                .andExpect(jsonPath("$.status").value("CREATED"));

        verify(userService).registerUser(eq("nuevousuario"), eq("Password123!"));
    }

    @Test
    void register_conRolesEnJson_ignoradosSinEfecto() throws Exception {
        // Enviar JSON con campo roles (que RegisterRequest no tiene)
        // debe ser ignorado por Jackson y no afectar al registro
        String jsonConRolesExtra = """
                {
                    "username": "testuser",
                    "password": "Password123!",
                    "roles": ["ROLE_ADMIN"]
                }
                """;

        doNothing().when(userService).registerUser(any(), any());

        mockMvc.perform(post("/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonConRolesExtra))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.status").value("CREATED"));

        // registerUser solo recibe username y password — el campo roles extra
        // del JSON es ignorado porque RegisterRequest no tiene ese campo
        verify(userService).registerUser(eq("testuser"), eq("Password123!"));
    }

    @Test
    void register_usernameYaExiste_debeRetornar409() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("existente");
        request.setPassword("Password123!");

        doThrow(new UsuarioDuplicadoException("existente"))
                .when(userService).registerUser(any(), any());

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

        doNothing().when(userService).changePasswordFromToken(anyString(), anyString(), anyString());

        mockMvc.perform(post("/auth/change-password").with(csrf())
                        .header("Authorization", "Bearer fake.jwt.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(userService).changePasswordFromToken(eq("Bearer fake.jwt.token"), eq("passwordActual"), eq("NuevaPassword123!"));
    }

    // =========================================================
    // POST /auth/refresh — Refresh Token Rotation
    // =========================================================

    @Test
    void refreshToken_exitoso_debeRetornar200ConNuevosTokens() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh.token.valido");

        AuthResponse expectedResponse = new AuthResponse(
                "nuevo.access.token", "nuevo.refresh.token",
                " Token refrescado exitosamente", "silvio");

        when(userService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(expectedResponse);

        mockMvc.perform(post("/auth/refresh").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("nuevo.access.token"))
                .andExpect(jsonPath("$.refreshToken").value("nuevo.refresh.token"))
                .andExpect(jsonPath("$.message").value(" Token refrescado exitosamente"))
                .andExpect(jsonPath("$.username").value("silvio"));

        verify(userService).refreshToken(any(RefreshTokenRequest.class));
    }

    @Test
    void refreshToken_expirado_debeRetornar401() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh.token.expirado");

        // El servicio lanza TokenExpiradoException → GlobalExceptionHandler devuelve 401
        when(userService.refreshToken(any(RefreshTokenRequest.class)))
                .thenThrow(new TokenExpiradoException(" Refresh token expirado"));

        mockMvc.perform(post("/auth/refresh").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(" Refresh token expirado"));

        verify(userService).refreshToken(any(RefreshTokenRequest.class));
    }

    @Test
    void refreshToken_tokenYaRotado_debeRetornar401() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("refresh.token.ya.rotado");

        // El servicio lanza TokenInvalidoException cuando el token ya fue rotado
        when(userService.refreshToken(any(RefreshTokenRequest.class)))
                .thenThrow(new TokenInvalidoException(
                        " Refresh token inválido o ya utilizado. Por favor inicia sesión nuevamente."));

        mockMvc.perform(post("/auth/refresh").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        " Refresh token inválido o ya utilizado. Por favor inicia sesión nuevamente."));

        verify(userService).refreshToken(any(RefreshTokenRequest.class));
    }

    @Test
    void refreshToken_tokenTipoAccess_debeRetornar401() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("access.token.falso");

        // El servicio lanza TokenInvalidoException si el token no es de tipo refresh
        when(userService.refreshToken(any(RefreshTokenRequest.class)))
                .thenThrow(new TokenInvalidoException(" Token inválido: no es un refresh token"));

        mockMvc.perform(post("/auth/refresh").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(" Token inválido: no es un refresh token"));

        verify(userService).refreshToken(any(RefreshTokenRequest.class));
    }

    // =========================================================
    // POST /auth/forgot-password — casos borde
    // =========================================================

    @Test
    void forgotPassword_usuarioNoExistente_debeRetornar404() throws Exception {
        PasswordResetRequest request = new PasswordResetRequest();
        request.setUsername("usuario_inexistente");

        // createPasswordResetToken lanza UsuarioNotFoundException si el usuario no existe
        doThrow(new UsuarioNotFoundException("usuario_inexistente"))
                .when(userService).createPasswordResetToken("usuario_inexistente");

        mockMvc.perform(post("/auth/forgot-password").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());

        verify(userService).createPasswordResetToken("usuario_inexistente");
    }
}
