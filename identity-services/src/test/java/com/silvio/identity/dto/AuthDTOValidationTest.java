package com.silvio.identity.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de validación de los DTOs de autenticación de identity-services.
 *
 * Verifica que las anotaciones @NotBlank y @Size se apliquen correctamente
 * en los campos de entrada: username, password, token, refreshToken.
 */
class AuthDTOValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    // =========================================================
    // AuthRequest — @NotBlank + @Size en username y password
    // =========================================================

    @Test
    void authRequest_usernameNulo_debeTenerViolacion() {
        AuthRequest dto = new AuthRequest();
        dto.setPassword("password123");

        Set<ConstraintViolation<AuthRequest>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("username")));
    }

    @Test
    void authRequest_usernameCorto_debeTenerViolacion() {
        AuthRequest dto = new AuthRequest();
        dto.setUsername("ab");      // min 3
        dto.setPassword("password123");

        Set<ConstraintViolation<AuthRequest>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("username")));
    }

    @Test
    void authRequest_passwordNulo_debeTenerViolacion() {
        AuthRequest dto = new AuthRequest();
        dto.setUsername("usuario1");

        Set<ConstraintViolation<AuthRequest>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("password")));
    }

    @Test
    void authRequest_passwordCorto_debeTenerViolacion() {
        AuthRequest dto = new AuthRequest();
        dto.setUsername("usuario1");
        dto.setPassword("12345");   // min 6

        Set<ConstraintViolation<AuthRequest>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("password")));
    }

    @Test
    void authRequest_valido_noDebeTenerViolacion() {
        AuthRequest dto = new AuthRequest();
        dto.setUsername("usuario1");
        dto.setPassword("password123");

        Set<ConstraintViolation<AuthRequest>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty(), "DTO valido no debe tener violaciones");
    }

    // =========================================================
    // RegisterRequest — @NotBlank + @Size en username, password; roles opcional
    // =========================================================

    @Test
    void registerRequest_usernameNulo_debeTenerViolacion() {
        RegisterRequest dto = new RegisterRequest();
        dto.setPassword("password123");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("username")));
    }

    @Test
    void registerRequest_passwordCorto_debeTenerViolacion() {
        RegisterRequest dto = new RegisterRequest();
        dto.setUsername("nuevouser");
        dto.setPassword("12345");   // min 6

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("password")));
    }

    @Test
    void registerRequest_rolesNulo_noDebeTenerViolacion() {
        RegisterRequest dto = new RegisterRequest();
        dto.setUsername("nuevouser");
        dto.setPassword("password123");
        dto.setRoles(null);         // opcional

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty(), "roles null es valido (opcional)");
    }

    @Test
    void registerRequest_valido_noDebeTenerViolacion() {
        RegisterRequest dto = new RegisterRequest();
        dto.setUsername("nuevouser");
        dto.setPassword("password123");
        dto.setRoles(Set.of("ROLE_USER"));

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty(), "DTO valido no debe tener violaciones");
    }

    // =========================================================
    // RefreshTokenRequest — @NotBlank + @Size en refreshToken
    // =========================================================

    @Test
    void refreshTokenRequest_tokenNulo_debeTenerViolacion() {
        RefreshTokenRequest dto = new RefreshTokenRequest();

        Set<ConstraintViolation<RefreshTokenRequest>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("refreshToken")));
    }

    @Test
    void refreshTokenRequest_tokenCorto_debeTenerViolacion() {
        RefreshTokenRequest dto = new RefreshTokenRequest();
        dto.setRefreshToken("12345");   // min 10

        Set<ConstraintViolation<RefreshTokenRequest>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("refreshToken")));
    }

    @Test
    void refreshTokenRequest_valido_noDebeTenerViolacion() {
        RefreshTokenRequest dto = new RefreshTokenRequest();
        dto.setRefreshToken("abcdefghij");  // >= 10 chars

        Set<ConstraintViolation<RefreshTokenRequest>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty());
    }

    // =========================================================
    // PasswordResetRequest — @NotBlank + @Size en username
    // =========================================================

    @Test
    void passwordResetRequest_usernameNulo_debeTenerViolacion() {
        PasswordResetRequest dto = new PasswordResetRequest();

        Set<ConstraintViolation<PasswordResetRequest>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("username")));
    }

    @Test
    void passwordResetRequest_usernameCorto_debeTenerViolacion() {
        PasswordResetRequest dto = new PasswordResetRequest();
        dto.setUsername("ab");   // min 3

        Set<ConstraintViolation<PasswordResetRequest>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("username")));
    }

    @Test
    void passwordResetRequest_valido_noDebeTenerViolacion() {
        PasswordResetRequest dto = new PasswordResetRequest();
        dto.setUsername("usuario1");

        Set<ConstraintViolation<PasswordResetRequest>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty());
    }

    // =========================================================
    // PasswordUpdateRequest — @NotBlank + @Size en token y newPassword
    // =========================================================

    @Test
    void passwordUpdateRequest_tokenNulo_debeTenerViolacion() {
        PasswordUpdateRequest dto = new PasswordUpdateRequest();
        dto.setNewPassword("newpassword123");

        Set<ConstraintViolation<PasswordUpdateRequest>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("token")));
    }

    @Test
    void passwordUpdateRequest_newPasswordNulo_debeTenerViolacion() {
        PasswordUpdateRequest dto = new PasswordUpdateRequest();
        dto.setToken("valid-token-here");

        Set<ConstraintViolation<PasswordUpdateRequest>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("newPassword")));
    }

    @Test
    void passwordUpdateRequest_valido_noDebeTenerViolacion() {
        PasswordUpdateRequest dto = new PasswordUpdateRequest();
        dto.setToken("valid-token-here-12345");
        dto.setNewPassword("newpassword123");

        Set<ConstraintViolation<PasswordUpdateRequest>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty());
    }

    // =========================================================
    // ChangePasswordRequest — @NotBlank + @Size en ambas contraseñas
    // =========================================================

    @Test
    void changePasswordRequest_currentPasswordNulo_debeTenerViolacion() {
        ChangePasswordRequest dto = new ChangePasswordRequest();
        dto.setNewPassword("newpassword123");

        Set<ConstraintViolation<ChangePasswordRequest>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("currentPassword")));
    }

    @Test
    void changePasswordRequest_newPasswordCorto_debeTenerViolacion() {
        ChangePasswordRequest dto = new ChangePasswordRequest();
        dto.setCurrentPassword("oldpass");
        dto.setNewPassword("12345");   // min 6

        Set<ConstraintViolation<ChangePasswordRequest>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("newPassword")));
    }

    @Test
    void changePasswordRequest_valido_noDebeTenerViolacion() {
        ChangePasswordRequest dto = new ChangePasswordRequest();
        dto.setCurrentPassword("oldpassword");
        dto.setNewPassword("newpassword123");

        Set<ConstraintViolation<ChangePasswordRequest>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty());
    }
}
