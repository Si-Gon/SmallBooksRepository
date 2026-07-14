package com.silvio.notification.dto;

import com.silvio.notification.model.Notificacion.TipoNotificacion;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de validación del DTO NotificacionRequestDTO.
 *
 * Verifica que @NotBlank/@Size en usuarioId y mensaje, y @NotNull en tipo
 * funcionen correctamente.
 */
class NotificacionRequestDTOValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void usuarioIdNulo_debeTenerViolacion() {
        NotificacionRequestDTO dto = new NotificacionRequestDTO();
        dto.setTipo(TipoNotificacion.PRESTAMO_CREADO);
        dto.setMensaje("Mensaje de prueba valido");

        Set<ConstraintViolation<NotificacionRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("usuarioId")),
                "usuarioId null debe violar @NotBlank");
    }

    @Test
    void usuarioIdVacio_debeTenerViolacion() {
        NotificacionRequestDTO dto = new NotificacionRequestDTO();
        dto.setUsuarioId("");
        dto.setTipo(TipoNotificacion.PRESTAMO_CREADO);
        dto.setMensaje("Mensaje de prueba valido");

        Set<ConstraintViolation<NotificacionRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("usuarioId")),
                "usuarioId vacio debe violar @NotBlank");
    }

    @Test
    void usuarioIdMuyLargo_debeTenerViolacion() {
        NotificacionRequestDTO dto = new NotificacionRequestDTO();
        dto.setUsuarioId("X".repeat(101)); // max 100
        dto.setTipo(TipoNotificacion.PRESTAMO_CREADO);
        dto.setMensaje("Mensaje de prueba valido");

        Set<ConstraintViolation<NotificacionRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("usuarioId")),
                "usuarioId > 100 debe violar @Size(max=100)");
    }

    @Test
    void tipoNulo_debeTenerViolacion() {
        NotificacionRequestDTO dto = new NotificacionRequestDTO();
        dto.setUsuarioId("usuario1");
        dto.setMensaje("Mensaje de prueba valido");

        Set<ConstraintViolation<NotificacionRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("tipo")),
                "tipo null debe violar @NotNull");
    }

    @Test
    void mensajeNulo_debeTenerViolacion() {
        NotificacionRequestDTO dto = new NotificacionRequestDTO();
        dto.setUsuarioId("usuario1");
        dto.setTipo(TipoNotificacion.PRESTAMO_CREADO);

        Set<ConstraintViolation<NotificacionRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("mensaje")),
                "mensaje null debe violar @NotBlank");
    }

    @Test
    void mensajeCorto_debeTenerViolacion() {
        NotificacionRequestDTO dto = new NotificacionRequestDTO();
        dto.setUsuarioId("usuario1");
        dto.setTipo(TipoNotificacion.PRESTAMO_CREADO);
        dto.setMensaje("abc"); // min 5

        Set<ConstraintViolation<NotificacionRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("mensaje")),
                "mensaje < 5 caracteres debe violar @Size(min=5)");
    }

    @Test
    void mensajeMuyLargo_debeTenerViolacion() {
        NotificacionRequestDTO dto = new NotificacionRequestDTO();
        dto.setUsuarioId("usuario1");
        dto.setTipo(TipoNotificacion.PRESTAMO_CREADO);
        dto.setMensaje("X".repeat(501)); // max 500

        Set<ConstraintViolation<NotificacionRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("mensaje")),
                "mensaje > 500 caracteres debe violar @Size(max=500)");
    }

    @Test
    void dtoValido_noDebeTenerViolacion() {
        NotificacionRequestDTO dto = new NotificacionRequestDTO();
        dto.setUsuarioId("usuario1");
        dto.setTipo(TipoNotificacion.PRESTAMO_CREADO);
        dto.setMensaje("Mensaje de prueba valido");

        Set<ConstraintViolation<NotificacionRequestDTO>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty(), "DTO valido no debe tener violaciones");
    }
}
