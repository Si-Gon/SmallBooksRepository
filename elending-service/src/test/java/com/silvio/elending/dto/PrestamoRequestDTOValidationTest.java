package com.silvio.elending.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de validación del DTO PrestamoRequestDTO.
 *
 * Verifica que @NotNull y @Positive en libroId funcionen correctamente.
 */
class PrestamoRequestDTOValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void libroIdNulo_debeTenerViolacion() {
        PrestamoRequestDTO dto = new PrestamoRequestDTO();
        // libroId queda null

        Set<ConstraintViolation<PrestamoRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("libroId")),
                "libroId null debe violar @NotNull");
    }

    @Test
    void libroIdCero_debeTenerViolacion() {
        PrestamoRequestDTO dto = new PrestamoRequestDTO();
        dto.setLibroId(0L); // 0 no es positivo

        Set<ConstraintViolation<PrestamoRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("libroId")),
                "libroId = 0 debe violar @Positive");
    }

    @Test
    void libroIdNegativo_debeTenerViolacion() {
        PrestamoRequestDTO dto = new PrestamoRequestDTO();
        dto.setLibroId(-1L); // negativo

        Set<ConstraintViolation<PrestamoRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("libroId")),
                "libroId negativo debe violar @Positive");
    }

    @Test
    void libroIdValido_noDebeTenerViolacion() {
        PrestamoRequestDTO dto = new PrestamoRequestDTO();
        dto.setLibroId(1L);

        Set<ConstraintViolation<PrestamoRequestDTO>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty(), "DTO valido no debe tener violaciones");
    }
}
