package com.silvio.catalog.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de validación directa del DTO LibroRequestDTO usando Jakarta Validator.
 *
 * Verifica que las anotaciones @NotBlank, @Pattern, @Size, @Min, @Max
 * funcionan correctamente sin necesidad de MockMvc ni el controller.
 */
class LibroRequestDTOValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    // =========================================================
    // @NotBlank en titulo
    // =========================================================

    @Test
    void tituloNulo_debeTenerViolacion() {
        LibroRequestDTO dto = crearDTOValido();
        dto.setTitulo(null);

        Set<ConstraintViolation<LibroRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty(), "Debe violar @NotBlank en titulo");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("titulo")));
    }

    @Test
    void tituloVacio_debeTenerViolacion() {
        LibroRequestDTO dto = crearDTOValido();
        dto.setTitulo("");

        Set<ConstraintViolation<LibroRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("titulo")));
    }

    @Test
    void tituloValido_noDebeTenerViolacion() {
        LibroRequestDTO dto = crearDTOValido();

        Set<ConstraintViolation<LibroRequestDTO>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty(), "DTO válido no debe tener violaciones");
    }

    // =========================================================
    // @NotBlank en autor
    // =========================================================

    @Test
    void autorNulo_debeTenerViolacion() {
        LibroRequestDTO dto = crearDTOValido();
        dto.setAutor(null);

        Set<ConstraintViolation<LibroRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("autor")));
    }

    @Test
    void autorVacio_debeTenerViolacion() {
        LibroRequestDTO dto = crearDTOValido();
        dto.setAutor("");

        Set<ConstraintViolation<LibroRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("autor")));
    }

    // =========================================================
    // @Pattern en isbn (ISBN-10 o ISBN-13)
    // =========================================================

    @Test
    void isbnNulo_debeTenerViolacion() {
        LibroRequestDTO dto = crearDTOValido();
        dto.setIsbn(null);

        Set<ConstraintViolation<LibroRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("isbn")));
    }

    @Test
    void isbnFormatoInvalido_debeTenerViolacion() {
        LibroRequestDTO dto = crearDTOValido();
        dto.setIsbn("ISBN-invalido");

        Set<ConstraintViolation<LibroRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("isbn")));
    }

    @Test
    void isbn10Valido_noDebeTenerViolacion() {
        LibroRequestDTO dto = crearDTOValido();
        dto.setIsbn("1234567890");

        Set<ConstraintViolation<LibroRequestDTO>> violations = validator.validate(dto);

        assertTrue(violations.stream().noneMatch(v -> v.getPropertyPath().toString().equals("isbn")),
                "ISBN-10 válido no debe tener violacion");
    }

    @Test
    void isbn13Valido_noDebeTenerViolacion() {
        LibroRequestDTO dto = crearDTOValido();
        dto.setIsbn("9781234567897");

        Set<ConstraintViolation<LibroRequestDTO>> violations = validator.validate(dto);

        assertTrue(violations.stream().noneMatch(v -> v.getPropertyPath().toString().equals("isbn")),
                "ISBN-13 válido no debe tener violacion");
    }

    @Test
    void isbn10ConXValido_noDebeTenerViolacion() {
        LibroRequestDTO dto = crearDTOValido();
        dto.setIsbn("123456789X");

        Set<ConstraintViolation<LibroRequestDTO>> violations = validator.validate(dto);

        assertTrue(violations.stream().noneMatch(v -> v.getPropertyPath().toString().equals("isbn")),
                "ISBN-10 con check digit X debe ser valido");
    }

    // =========================================================
    // @Size en editorial
    // =========================================================

    @Test
    void editorialMuyLarga_debeTenerViolacion() {
        LibroRequestDTO dto = crearDTOValido();
        dto.setEditorial("X".repeat(151)); // max 150

        Set<ConstraintViolation<LibroRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("editorial")));
    }

    // =========================================================
    // @Min / @Max en anioPublicacion
    // =========================================================

    @Test
    void anioPublicacionMuyAntiguo_debeTenerViolacion() {
        LibroRequestDTO dto = crearDTOValido();
        dto.setAnioPublicacion(1400); // < 1450

        Set<ConstraintViolation<LibroRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("anioPublicacion")));
    }

    @Test
    void anioPublicacionFuturo_debeTenerViolacion() {
        LibroRequestDTO dto = crearDTOValido();
        dto.setAnioPublicacion(2101); // > 2100

        Set<ConstraintViolation<LibroRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("anioPublicacion")));
    }

    @Test
    void anioPublicacionValido_noDebeTenerViolacion() {
        LibroRequestDTO dto = crearDTOValido();
        dto.setAnioPublicacion(2024);

        Set<ConstraintViolation<LibroRequestDTO>> violations = validator.validate(dto);

        assertTrue(violations.stream().noneMatch(v -> v.getPropertyPath().toString().equals("anioPublicacion")));
    }

    // =========================================================
    // @Pattern en portadaUrl
    // =========================================================

    @Test
    void portadaUrlSinHttp_debeTenerViolacion() {
        LibroRequestDTO dto = crearDTOValido();
        dto.setPortadaUrl("ftp://mal.com/img.jpg");

        Set<ConstraintViolation<LibroRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("portadaUrl")));
    }

    @Test
    void portadaUrlNula_noDebeTenerViolacion() {
        LibroRequestDTO dto = crearDTOValido();
        dto.setPortadaUrl(null); // campo opcional

        Set<ConstraintViolation<LibroRequestDTO>> violations = validator.validate(dto);

        assertTrue(violations.stream().noneMatch(v -> v.getPropertyPath().toString().equals("portadaUrl")),
                "portadaUrl null no debe tener violacion (es opcional)");
    }

    @Test
    void portadaUrlValida_noDebeTenerViolacion() {
        LibroRequestDTO dto = crearDTOValido();
        dto.setPortadaUrl("https://ejemplo.com/portada.jpg");

        Set<ConstraintViolation<LibroRequestDTO>> violations = validator.validate(dto);

        assertTrue(violations.stream().noneMatch(v -> v.getPropertyPath().toString().equals("portadaUrl")),
                "URL valida con https no debe tener violacion");
    }

    // =========================================================
    // @Size en sinopsis
    // =========================================================

    @Test
    void sinopsisMuyLarga_debeTenerViolacion() {
        LibroRequestDTO dto = crearDTOValido();
        dto.setSinopsis("X".repeat(2001)); // max 2000

        Set<ConstraintViolation<LibroRequestDTO>> violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("sinopsis")));
    }

    @Test
    void sinopsisNula_noDebeTenerViolacion() {
        LibroRequestDTO dto = crearDTOValido();
        dto.setSinopsis(null); // opcional

        Set<ConstraintViolation<LibroRequestDTO>> violations = validator.validate(dto);

        assertTrue(violations.stream().noneMatch(v -> v.getPropertyPath().toString().equals("sinopsis")),
                "sinopsis null no debe tener violacion (opcional)");
    }

    @Test
    void sinopsisValida_noDebeTenerViolacion() {
        LibroRequestDTO dto = crearDTOValido();
        dto.setSinopsis("Una breve sinopsis");

        Set<ConstraintViolation<LibroRequestDTO>> violations = validator.validate(dto);

        assertTrue(violations.stream().noneMatch(v -> v.getPropertyPath().toString().equals("sinopsis")));
    }

    // =========================================================
    // Helper
    // =========================================================

    private LibroRequestDTO crearDTOValido() {
        LibroRequestDTO dto = new LibroRequestDTO();
        dto.setTitulo("Cien años de soledad");
        dto.setAutor("Gabriel García Márquez");
        dto.setIsbn("9788437604947");
        dto.setEditorial("Cátedra");
        dto.setAnioPublicacion(2024);
        dto.setIdioma("Español");
        dto.setGenero("Novela");
        dto.setSinopsis("Una obra maestra de la literatura universal.");
        dto.setPortadaUrl("https://ejemplo.com/portada.jpg");
        return dto;
    }
}
