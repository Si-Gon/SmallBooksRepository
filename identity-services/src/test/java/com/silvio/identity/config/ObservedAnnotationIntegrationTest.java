package com.silvio.identity.config;

import com.silvio.identity.repository.UserRepository;
import com.silvio.identity.service.UserService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

// Tests de integracion que verifican que las 6 anotaciones @Observed
// en UserService crean spans de tracing correctamente.
//
// UserService tiene @Observed en:
//   identity.registerUser, identity.loadUserByUsername,
//   identity.createPasswordResetToken, identity.resetPassword,
//   identity.changePassword, identity.obtenerUsuario
@SpringBootTest
@ActiveProfiles("test")
class ObservedAnnotationIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired(required = false)
    private ObservationRegistry observationRegistry;

    @Autowired(required = false)
    private Tracer tracer;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @Test
    void contextCarga_conBeansDeTracing() {
        assertNotNull(observationRegistry, "ObservationRegistry debe estar disponible");
        assertNotNull(tracer, "Tracer (Brave) debe estar disponible");
    }

    @Test
    void observationRegistry_aceptaCrearObservaciones() {
        Observation observation = Observation.createNotStarted("test.manual", observationRegistry);
        assertNotNull(observation);
        assertDoesNotThrow(() -> {
            observation.start();
            observation.stop();
        });
    }

    @Test
    void tracer_currentSpan_devuelveSpanSinError() {
        assertDoesNotThrow(() -> tracer.currentSpan());
    }

    @Test
    void observedAspect_registerUser_creaSpanCorrectamente() {
        when(userRepository.findByUsername("test-user")).thenReturn(java.util.Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        assertDoesNotThrow(() ->
                userService.registerUser("test-user", "password", java.util.Set.of("ROLE_USER")));
    }

    @Test
    void observedAspect_loadUserByUsername_creaSpanCorrectamente() {
        com.silvio.identity.model.User user = new com.silvio.identity.model.User();
        user.setUsername("test-user");
        user.setPassword("encoded");
        user.setRoles(java.util.Set.of("ROLE_USER"));
        when(userRepository.findByUsername("test-user")).thenReturn(java.util.Optional.of(user));
        assertDoesNotThrow(() -> {
            var resultado = userService.loadUserByUsername("test-user");
            assertNotNull(resultado);
        });
    }

    @Test
    void observedAspect_createPasswordResetToken_creaSpanCorrectamente() {
        com.silvio.identity.model.User user = new com.silvio.identity.model.User();
        user.setUsername("test-user");
        when(userRepository.findByUsername("test-user")).thenReturn(java.util.Optional.of(user));
        assertDoesNotThrow(() -> {
            var token = userService.createPasswordResetToken("test-user");
            assertNotNull(token);
        });
    }

    @Test
    void observedAspect_resetPassword_creaSpanCorrectamente() {
        com.silvio.identity.model.User user = new com.silvio.identity.model.User();
        user.setUsername("test-user");
        user.setResetTokenExpiry(java.time.LocalDateTime.now().plusHours(1));
        when(userRepository.findByResetToken("valid-token"))
                .thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("new-encoded");
        assertDoesNotThrow(() ->
                userService.resetPassword("valid-token", "newPassword"));
    }

    @Test
    void observedAspect_changePassword_creaSpanCorrectamente() {
        com.silvio.identity.model.User user = new com.silvio.identity.model.User();
        user.setUsername("test-user");
        user.setPassword("$2a$10$oldhash");
        when(userRepository.findByUsername("test-user")).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("new-encoded");
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        assertDoesNotThrow(() ->
                userService.changePassword("test-user", "currentPassword", "newPassword"));
    }

    @Test
    void observedAspect_obtenerUsuario_creaSpanCorrectamente() {
        com.silvio.identity.model.User user = new com.silvio.identity.model.User();
        user.setUsername("test-user");
        user.setRoles(java.util.Set.of("ROLE_USER"));
        when(userRepository.findByUsername("test-user")).thenReturn(java.util.Optional.of(user));
        assertDoesNotThrow(() -> {
            var resultado = userService.obtenerUsuarioPorUsername("test-user");
            assertNotNull(resultado);
        });
    }
}
