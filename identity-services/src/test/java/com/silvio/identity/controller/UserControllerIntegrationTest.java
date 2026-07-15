package com.silvio.identity.controller;

import com.silvio.identity.dto.UsuarioDTO;
import com.silvio.identity.model.User;
import com.silvio.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de integración del endpoint GET /api/users/{username}.
 *
 * Verifica el comportamiento completo con la seguridad real (SecurityConfig + JwtAuthenticationFilter)
 * a diferencia de @WebMvcTest que usa permitAll.
 *
 * Estrategia:
 * 1. Sin autenticación → 403 (SecurityConfig exige autenticación para cualquier request,
 *    pero no usa httpBasic(), por lo que el entry point por defecto devuelve 403)
 * 2. Con @WithMockUser (simula usuario autenticado) → la seguridad permite el paso,
 *    y el controlador responde según la lógica de negocio (200 si existe, 404 si no)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void obtenerUsuario_sinAuth_debeRetornar403() throws Exception {
        // When & Then — sin autenticación, SecurityConfig rechaza con 403
        // (Spring Security 6 sin httpBasic() usa Http403ForbiddenEntryPoint por defecto)
        mockMvc.perform(get("/api/users/testuser"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "silvio", roles = "USER")
    void obtenerUsuario_conMockUser_usuarioNoExistente_debeRetornar404() throws Exception {
        // When & Then — autenticado pero el username no existe en BD → 404
        mockMvc.perform(get("/api/users/noexiste"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = "silvio", roles = "USER")
    void obtenerUsuario_conMockUser_usuarioExistente_debeRetornar200() throws Exception {
        // Given — crear un usuario en BD para el test
        User user = new User();
        user.setUsername("testuser");
        user.setPassword("$2a$10$dummyhashedpassword");
        user.setRoles(Set.of("ROLE_USER"));
        userRepository.save(user);

        // When & Then — autenticado y usuario existe → 200 con datos
        mockMvc.perform(get("/api/users/testuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"));
    }
}
