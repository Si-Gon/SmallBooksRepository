package com.silvio.identity.service;

import com.silvio.identity.config.JwtProperties;
import com.silvio.identity.dto.UsuarioDTO;
import com.silvio.identity.exception.ContrasenaIncorrectaException;
import com.silvio.identity.exception.TokenExpiradoException;
import com.silvio.identity.exception.TokenInvalidoException;
import com.silvio.identity.exception.UsuarioDuplicadoException;
import com.silvio.identity.exception.UsuarioNotFoundException;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProperties jwtProperties;

    // ─── helpers ─────────────────────────────────────────────────────────────

    private User usuarioBase(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("$2a$10$hashedpassword");
        user.setRoles(Set.of("ROLE_USER"));
        return user;
    }

    // Reproduce el mismo hash SHA-256 que usa UserService.hashRefreshToken()
    private String hashTokenForTest(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
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
        UsuarioDuplicadoException ex = assertThrows(UsuarioDuplicadoException.class,
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
        when(jwtProperties.getResetTokenExpirationHours()).thenReturn(24L);

        // When
        String token = userService.createPasswordResetToken(username);

        // Then
        assertNotNull(token);
        assertFalse(token.isEmpty());
        // El token debe ser un UUID válido
        assertDoesNotThrow(() -> UUID.fromString(token));
        verify(userRepository).save(any(User.class));
        verify(jwtProperties, atLeastOnce()).getResetTokenExpirationHours();
    }

    @Test
    void createPasswordResetToken_estableceResetTokenExpiry() {
        // Given
        String username = "silvio";
        User user = usuarioBase(username);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtProperties.getResetTokenExpirationHours()).thenReturn(48L);

        // When
        userService.createPasswordResetToken(username);

        // Then
        assertNotNull(user.getResetTokenExpiry());
        // Debe ser aproximadamente ahora + 48h (con un margen de 1 minuto)
        LocalDateTime expectedMin = LocalDateTime.now().plusHours(48).minusMinutes(1);
        LocalDateTime expectedMax = LocalDateTime.now().plusHours(48).plusMinutes(1);
        assertTrue(user.getResetTokenExpiry().isAfter(expectedMin),
                "resetTokenExpiry debe ser >= ahora + 48h");
        assertTrue(user.getResetTokenExpiry().isBefore(expectedMax),
                "resetTokenExpiry debe ser <= ahora + 48h");
        verify(userRepository).save(any(User.class));
        verify(jwtProperties, atLeastOnce()).getResetTokenExpirationHours();
    }

    @Test
    void createPasswordResetToken_falla_cuando_usuario_no_existe() {
        // Given
        when(userRepository.findByUsername("noexiste")).thenReturn(Optional.empty());

        // When & Then
        assertThrows(UsuarioNotFoundException.class,
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
        TokenExpiradoException ex = assertThrows(TokenExpiradoException.class,
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
        assertThrows(TokenInvalidoException.class,
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
        ContrasenaIncorrectaException ex = assertThrows(ContrasenaIncorrectaException.class,
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
        assertThrows(UsuarioNotFoundException.class,
                () -> userService.changePassword("noexiste", "pass", "nueva"));
        verify(userRepository, never()).save(any(User.class));
    }

    // ─── tests storeRefreshTokenHash ──────────────────────────────────────────

    @Test
    void storeRefreshTokenHash_almacena_hash_cuando_usuario_existe() {
        // Given
        String username = "silvio";
        String refreshToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzaWx2aW8iLCJ0eXBlIjoicmVmcmVzaCJ9.sometoken";
        User user = usuarioBase(username);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        // When
        userService.storeRefreshTokenHash(username, refreshToken);

        // Then
        verify(userRepository).findByUsername(username);
        verify(userRepository).save(any(User.class));
        // Verificar que se asignó un hash (SHA-256 = 64 caracteres hex)
        assertNotNull(user.getRefreshTokenHash());
        assertEquals(64, user.getRefreshTokenHash().length());
    }

    @Test
    void storeRefreshTokenHash_falla_cuando_usuario_no_existe() {
        // Given
        when(userRepository.findByUsername("noexiste")).thenReturn(Optional.empty());

        // When & Then
        assertThrows(UsuarioNotFoundException.class,
                () -> userService.storeRefreshTokenHash("noexiste", "token"));
        verify(userRepository, never()).save(any(User.class));
    }

    // ─── tests rotateRefreshToken ─────────────────────────────────────────────

    @Test
    void rotateRefreshToken_rotacion_exitosa_con_token_valido() {
        // Given
        String username = "silvio";
        String oldRefreshToken = "old-refresh-token-value";
        String newRefreshToken = "new-refresh-token-value";
        User user = usuarioBase(username);
        // Calcular el hash SHA-256 que coincide con el que genera UserService internamente
        String expectedOldHash = hashTokenForTest(oldRefreshToken);
        user.setRefreshTokenHash(expectedOldHash);

        when(userRepository.findByRefreshTokenHash(expectedOldHash)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        // When
        userService.rotateRefreshToken(oldRefreshToken, newRefreshToken);

        // Then
        verify(userRepository).findByRefreshTokenHash(expectedOldHash);
        verify(userRepository).save(any(User.class));
        // El hash debe haber cambiado al del nuevo token
        assertNotNull(user.getRefreshTokenHash());
        assertEquals(64, user.getRefreshTokenHash().length());
    }

    @Test
    void rotateRefreshToken_falla_con_token_invalido() {
        // Given
        String oldRefreshToken = "token-que-no-existe";
        // Ningún usuario tiene este hash almacenado
        when(userRepository.findByRefreshTokenHash(anyString())).thenReturn(Optional.empty());

        // When & Then
        TokenInvalidoException ex = assertThrows(TokenInvalidoException.class,
                () -> userService.rotateRefreshToken(oldRefreshToken, "nuevo-token"));
        assertTrue(ex.getMessage().contains("inválido") || ex.getMessage().contains("utilizado"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void rotateRefreshToken_tokenYaRotado_debeFallar() {
        // Given — simula el escenario de reuso de un token ya rotado:
        // 1. El usuario tenía "old-token" cuyo hash estaba en BD
        // 2. Se hizo un refresh exitoso: el hash se actualizó al de "new-token"
        // 3. Un atacante intenta reusar "old-token" → ya no existe en BD

        String username = "silvio";
        String oldRefreshToken = "primer-refresh-token";
        String newRefreshToken = "segundo-refresh-token";

        User user = usuarioBase(username);

        // Primera rotación: el hash del old-token SÍ existe
        String firstHash = hashTokenForTest(oldRefreshToken);
        user.setRefreshTokenHash(firstHash);
        when(userRepository.findByRefreshTokenHash(firstHash)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        // Ejecutar primera rotación — debe funcionar
        userService.rotateRefreshToken(oldRefreshToken, newRefreshToken);
        // Después de la rotación, el hash ahora es el del nuevo token
        String newHash = hashTokenForTest(newRefreshToken);
        assertEquals(newHash, user.getRefreshTokenHash());

        // Segunda rotación con el MISMO old token — simula reuso
        // El hash del old-token ya no encuentra al usuario porque fue reemplazado
        when(userRepository.findByRefreshTokenHash(firstHash)).thenReturn(Optional.empty());

        TokenInvalidoException ex = assertThrows(TokenInvalidoException.class,
                () -> userService.rotateRefreshToken(oldRefreshToken, "otro-token-mas"));
        assertTrue(ex.getMessage().contains("inválido") || ex.getMessage().contains("utilizado"));
        verify(userRepository, times(2)).findByRefreshTokenHash(firstHash);
    }

    // ─── tests obtenerUsuarioPorUsername ────────────────────────────────────────

    @Test
    void obtenerUsuarioPorUsername_devuelve_DTO_cuando_usuario_existe() {
        // Given
        String username = "silvio";
        User user = usuarioBase(username);
        user.setId(1L);

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        // When
        UsuarioDTO dto = userService.obtenerUsuarioPorUsername(username);

        // Then
        assertNotNull(dto);
        assertEquals(1L, dto.getId());
        assertEquals(username, dto.getUsername());
        assertTrue(dto.getRoles().contains("ROLE_USER"));
        assertEquals(1, dto.getRoles().size());
        verify(userRepository).findByUsername(username);
    }

    @Test
    void obtenerUsuarioPorUsername_devuelve_DTO_con_multiples_roles() {
        // Given — usuario con múltiples roles
        String username = "admin";
        User user = usuarioBase(username);
        user.setId(2L);
        user.setRoles(Set.of("ROLE_USER", "ROLE_ADMIN", "ROLE_PREMIUM"));

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));

        // When
        UsuarioDTO dto = userService.obtenerUsuarioPorUsername(username);

        // Then — el DTO debe contener todos los roles
        assertNotNull(dto);
        assertEquals(2L, dto.getId());
        assertEquals(username, dto.getUsername());
        assertEquals(3, dto.getRoles().size());
        assertTrue(dto.getRoles().containsAll(Set.of("ROLE_USER", "ROLE_ADMIN", "ROLE_PREMIUM")));
        verify(userRepository).findByUsername(username);
    }

    @Test
    void obtenerUsuarioPorUsername_falla_cuando_usuario_no_existe() {
        // Given
        String username = "noexiste";
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        // When & Then
        UsernameNotFoundException ex = assertThrows(UsernameNotFoundException.class,
                () -> userService.obtenerUsuarioPorUsername(username));

        assertTrue(ex.getMessage().contains("no encontrado"));
        verify(userRepository).findByUsername(username);
    }

    @Test
    void obtenerUsuarioPorUsername_falla_cuando_username_es_null() {
        // Given — Spring Data JPA devuelve Optional.empty() para parámetros null
        when(userRepository.findByUsername(null)).thenReturn(Optional.empty());

        // When & Then — debe lanzar UsernameNotFoundException como con cualquier usuario inexistente
        UsernameNotFoundException ex = assertThrows(UsernameNotFoundException.class,
                () -> userService.obtenerUsuarioPorUsername(null));

        assertTrue(ex.getMessage().contains("no encontrado"));
        verify(userRepository).findByUsername(null);
    }
}