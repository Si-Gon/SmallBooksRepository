package com.silvio.subscription.dto;

import com.silvio.subscription.model.Suscripcion.PlanSuscripcion;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de validación del DTO SuscripcionRequestDTO.
 *
 * Verifica que @NotNull en plan, @NotNull/@Min/@Max en meses funcionen.
 */
class SuscripcionRequestDTOValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void planNulo_debeTenerViolacion() {
        SuscripcionRequestDTO dto = new SuscripcionRequestDTO();
        dto.setMeses(6);
        // plan queda null

        Set<ConstraintViolation<SuscripcionRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("plan")),
                "plan null debe violar @NotNull");
    }

    @Test
    void mesesNulo_debeTenerViolacion() {
        SuscripcionRequestDTO dto = new SuscripcionRequestDTO();
        dto.setPlan(PlanSuscripcion.PREMIUM);
        // meses queda null

        Set<ConstraintViolation<SuscripcionRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("meses")),
                "meses null debe violar @NotNull");
    }

    @Test
    void mesesCero_debeTenerViolacion() {
        SuscripcionRequestDTO dto = new SuscripcionRequestDTO();
        dto.setPlan(PlanSuscripcion.PREMIUM);
        dto.setMeses(0); // min 1

        Set<ConstraintViolation<SuscripcionRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("meses")),
                "meses=0 debe violar @Min(1)");
    }

    @Test
    void mesesExcesivo_debeTenerViolacion() {
        SuscripcionRequestDTO dto = new SuscripcionRequestDTO();
        dto.setPlan(PlanSuscripcion.PREMIUM);
        dto.setMeses(13); // max 12

        Set<ConstraintViolation<SuscripcionRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("meses")),
                "meses=13 debe violar @Max(12)");
    }

    @Test
    void mesesValido_noDebeTenerViolacion() {
        SuscripcionRequestDTO dto = new SuscripcionRequestDTO();
        dto.setPlan(PlanSuscripcion.BASICO);
        dto.setMeses(1);

        Set<ConstraintViolation<SuscripcionRequestDTO>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty(), "DTO valido no debe tener violaciones");
    }

    @Test
    void mesesDefault_noDebeTenerViolacion() {
        SuscripcionRequestDTO dto = new SuscripcionRequestDTO();
        dto.setPlan(PlanSuscripcion.PREMIUM);
        // meses usa default = 1

        Set<ConstraintViolation<SuscripcionRequestDTO>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty(), "DTO con meses por defecto no debe tener violaciones");
    }
}
