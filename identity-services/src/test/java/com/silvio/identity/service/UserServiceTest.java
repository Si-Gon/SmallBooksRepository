package com.silvio.identity.service;

import com.silvio.identity.model.User;
import com.silvio.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    // ─── helpers ─────────────────────────────────────────────────────────────

    private User usuarioBase(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("$2a$10$hashedpassword");
        user.setRoles(Set.of("ROLE_USER"));
        return user;
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ─── tests registerUser ──────────────────────────────────────────────────

    @Test
    void registerUser_exitoso_con_roles_personalizados() {
        // Given
        String username = "silvio";
        String rawPassword = "password123";
        Set<String> roles = Set.of("ROLE_PREMIUM");

        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(rawPassword)).thenReturn("$2a$10$hashedpassword");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        // When
        userService.registerUser(username, rawPassword, roles);

        // Then
        verify(userRepository).findByUsername(username);
        verify(passwordEncoder).encode(rawPassword);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerUser_exitoso_asigna_ROLE_USER_por_defecto_si_roles_es_null() {
        // Given
        String username = "nuevo";
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User saved = i.getArgument(0);
            // Verificar que se asignó ROLE_USER por defecto
            assertTrue(saved.getRoles().contains("ROLE_USER"));
            return saved;
        });

        // When
        userService.registerUser(username, "pass123", null);

        // Then
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerUser_falla_cuando_username_ya_existe() {
        // Given
        String username = "admin";
        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(usuarioBase(username)));

        // When & Then
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.registerUser(username, "pass123", null));

        assertTrue(ex.getMessage().contains("ya existe") || ex.getMessage().contains(username));
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(any());
    }

    // ─── tests loadUserByUsername ─────────────────────────────────────────────

    @Test
    void loadUserByUsername_devuelve_UserDetails_cuando_usuario_existe() {
        // Given
        String username = "silvio";
        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(usuarioBase(username)));

        // When
        UserDetails resultado = userService.loadUserByUsername(username);

        // Then
        assertNotNull(resultado);
        assertEquals(username, resultado.getUsername());
        assertFalse(resultado.getAuthorities().isEmpty());
        verify(userRepository).findByUsername(username);
    }

    @Test
    void loadUserByUsername_lanza_excepcion_cuando_usuario_no_existe() {
        // Given
        String username = "noexiste";
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(UsernameNotFoundException.class,
                () -> userService.loadUserByUsername(username));
        verify(userRepository).findByUsername(username);
    }

    // ─── tests createPasswordResetToken ──────────────────────────────────────

    @Test
    void createPasswordResetToken_genera_token_para_usuario_existente() {
        // Given
        String username = "silvio";
        User user = usuarioBase(username);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        // When
        String token = userService.createPasswordResetToken(username);

        // Then
        assertNotNull(token);
        assertFalse(token.isEmpty());
        // El token debe ser un UUID válido
        assertDoesNotThrow(() -> UUID.fromString(token));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createPasswordResetToken_falla_cuando_usuario_no_existe() {
        // Given
        when(userRepository.findByUsername("noexiste")).thenReturn(Optional.empty());

        // When & Then
        assertThrows(RuntimeException.class,
                () -> userService.createPasswordResetToken("noexiste"));
        verify(userRepository, never()).save(any(User.class));
    }

    // ─── tests resetPassword ─────────────────────────────────────────────────

    @Test
    void resetPassword_exitoso_con_token_valido() {
        // Given
        String token = UUID.randomUUID().toString();
        User user = usuarioBase("silvio");
        user.setResetToken(token);
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(1)); // no expirado

        when(userRepository.findByResetToken(token)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("nuevaPassword123")).thenReturn("$2a$10$newHash");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        // When
        userService.resetPassword(token, "nuevaPassword123");

        // Then
        verify(passwordEncoder).encode("nuevaPassword123");
        verify(userRepository).save(any(User.class));
        // El token debe quedar limpio después del reset
        assertNull(user.getResetToken());
        assertNull(user.getResetTokenExpiry());
    }

    @Test
    void resetPassword_falla_con_token_expirado() {
        // Given
        String token = UUID.randomUUID().toString();
        User user = usuarioBase("silvio");
        user.setResetToken(token);
        user.setResetTokenExpiry(LocalDateTime.now().minusHours(1)); // ya expiró

        when(userRepository.findByResetToken(token)).thenReturn(Optional.of(user));

        // When & Then
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.resetPassword(token, "nuevaPassword"));

        assertTrue(ex.getMessage().contains("expirado") || ex.getMessage().contains("token"));
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void resetPassword_falla_con_token_invalido() {
        // Given
        String tokenInvalido = "token-que-no-existe";
        when(userRepository.findByResetToken(tokenInvalido)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(RuntimeException.class,
                () -> userService.resetPassword(tokenInvalido, "nuevaPassword"));
        verify(userRepository, never()).save(any(User.class));
    }

    // ─── tests changePassword ─────────────────────────────────────────────────

    @Test
    void changePassword_exitoso_con_contrasena_actual_correcta() {
        // Given
        String username = "silvio";
        User user = usuarioBase(username);

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("passwordActual", user.getPassword())).thenReturn(true);
        when(passwordEncoder.encode("passwordNueva")).thenReturn("$2a$10$newHash");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        // When
        userService.changePassword(username, "passwordActual", "passwordNueva");

        // Then
        verify(passwordEncoder).matches("passwordActual", "$2a$10$hashedpassword");
        verify(passwordEncoder).encode("passwordNueva");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void changePassword_falla_con_contrasena_actual_incorrecta() {
        // Given
        String username = "silvio";
        User user = usuarioBase(username);

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("passwordIncorrecta", user.getPassword())).thenReturn(false);

        // When & Then
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.changePassword(username, "passwordIncorrecta", "nueva"));

        assertTrue(ex.getMessage().contains("incorrecta") || ex.getMessage().contains("actual"));
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void changePassword_falla_cuando_usuario_no_existe() {
        // Given
        when(userRepository.findByUsername("noexiste")).thenReturn(Optional.empty());

        // When & Then
        assertThrows(RuntimeException.class,
                () -> userService.changePassword("noexiste", "pass", "nueva"));
        verify(userRepository, never()).save(any(User.class));
    }
}