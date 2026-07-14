package com.silvio.license.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de validación del DTO LicenseRequestDTO.
 *
 * Verifica que @NotNull/@Positive en libroId y @NotNull/@Min/@Max en
 * totalCopias funcionen correctamente.
 */
class LicenseRequestDTOValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void libroIdNulo_debeTenerViolacion() {
        LicenseRequestDTO dto = new LicenseRequestDTO();
        dto.setTotalCopias(5);

        Set<ConstraintViolation<LicenseRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("libroId")),
                "libroId null debe violar @NotNull");
    }

    @Test
    void libroIdCero_debeTenerViolacion() {
        LicenseRequestDTO dto = new LicenseRequestDTO();
        dto.setLibroId(0L); // 0 no es positivo
        dto.setTotalCopias(5);

        Set<ConstraintViolation<LicenseRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("libroId")),
                "libroId=0 debe violar @Positive");
    }

    @Test
    void libroIdNegativo_debeTenerViolacion() {
        LicenseRequestDTO dto = new LicenseRequestDTO();
        dto.setLibroId(-1L);
        dto.setTotalCopias(5);

        Set<ConstraintViolation<LicenseRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("libroId")),
                "libroId negativo debe violar @Positive");
    }

    @Test
    void totalCopiasNulo_debeTenerViolacion() {
        LicenseRequestDTO dto = new LicenseRequestDTO();
        dto.setLibroId(1L);
        // totalCopias queda null

        Set<ConstraintViolation<LicenseRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("totalCopias")),
                "totalCopias null debe violar @NotNull");
    }

    @Test
    void totalCopiasCero_debeTenerViolacion() {
        LicenseRequestDTO dto = new LicenseRequestDTO();
        dto.setLibroId(1L);
        dto.setTotalCopias(0); // min 1

        Set<ConstraintViolation<LicenseRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("totalCopias")),
                "totalCopias=0 debe violar @Min(1)");
    }

    @Test
    void totalCopiasExcesivo_debeTenerViolacion() {
        LicenseRequestDTO dto = new LicenseRequestDTO();
        dto.setLibroId(1L);
        dto.setTotalCopias(10001); // max 10000

        Set<ConstraintViolation<LicenseRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("totalCopias")),
                "totalCopias=10001 debe violar @Max(10000)");
    }

    @Test
    void dtoValido_noDebeTenerViolacion() {
        LicenseRequestDTO dto = new LicenseRequestDTO();
        dto.setLibroId(1L);
        dto.setTotalCopias(5);

        Set<ConstraintViolation<LicenseRequestDTO>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty(), "DTO valido no debe tener violaciones");
    }
}
