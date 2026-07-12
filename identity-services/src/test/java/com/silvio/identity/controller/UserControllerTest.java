package com.silvio.identity.controller;

import com.silvio.identity.config.SecurityConfig;
import com.silvio.identity.dto.UsuarioDTO;
import com.silvio.identity.security.JwtAuthenticationFilter;
import com.silvio.identity.service.UserService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests del UserController.
 *
 * Misma estrategia que AuthControllerTest:
 * 1. excludeFilters excluye SecurityConfig y JwtAuthenticationFilter
 * 2. TestSecurityConfig reemplaza la seguridad real con permitAll
 */
@WebMvcTest(
    value = UserController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = { SecurityConfig.class, JwtAuthenticationFilter.class }
    )
)
@ActiveProfiles("test")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

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
    // GET /api/users/{username}
    // =========================================================

    @Test
    void obtenerUsuario_exitoso_debeRetornar200ConDatos() throws Exception {
        // Given
        String username = "silvio";
        UsuarioDTO dto = new UsuarioDTO();
        dto.setId(1L);
        dto.setUsername(username);
        dto.setRoles(Set.of("ROLE_USER"));

        when(userService.obtenerUsuarioPorUsername(username)).thenReturn(dto);

        // When & Then
        mockMvc.perform(get("/api/users/{username}", username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"));

        verify(userService).obtenerUsuarioPorUsername(username);
    }

    @Test
    void obtenerUsuario_noExistente_debeRetornar404() throws Exception {
        // Given
        String username = "noexiste";
        when(userService.obtenerUsuarioPorUsername(username))
                .thenThrow(new UsernameNotFoundException("Usuario '" + username + "' no encontrado"));

        // When & Then
        mockMvc.perform(get("/api/users/{username}", username))
                .andExpect(status().isNotFound());

        verify(userService).obtenerUsuarioPorUsername(username);
    }

    @Test
    void obtenerUsuario_conMultiplesRoles_debeRetornar200ConRolesCompletos() throws Exception {
        // Given — usuario con múltiples roles
        String username = "admin";
        UsuarioDTO dto = new UsuarioDTO();
        dto.setId(2L);
        dto.setUsername(username);
        dto.setRoles(Set.of("ROLE_USER", "ROLE_ADMIN", "ROLE_PREMIUM"));

        when(userService.obtenerUsuarioPorUsername(username)).thenReturn(dto);

        // When & Then — debe devolver todos los roles en el array JSON
        mockMvc.perform(get("/api/users/{username}", username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2L))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.roles.length()").value(3))
                .andExpect(jsonPath("$.roles", containsInAnyOrder("ROLE_USER", "ROLE_ADMIN", "ROLE_PREMIUM")));

        verify(userService).obtenerUsuarioPorUsername(username);
    }

    @Test
    void obtenerUsuario_conEmailComoUsername_debeRetornar200() throws Exception {
        // Given — usuario con email como username (caracteres especiales)
        String username = "usuario@correo.com";
        UsuarioDTO dto = new UsuarioDTO();
        dto.setId(3L);
        dto.setUsername(username);
        dto.setRoles(Set.of("ROLE_USER"));

        when(userService.obtenerUsuarioPorUsername(username)).thenReturn(dto);

        // When & Then — debe manejar correctamente caracteres como @ y .
        mockMvc.perform(get("/api/users/{username}", username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3L))
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"));

        verify(userService).obtenerUsuarioPorUsername(username);
    }
}
