package com.silvio.catalog.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test que verifica que el procesamiento de anotaciones de Lombok
 * genera correctamente los metodos en bytecode.
 *
 * Si este test falla, el problema esta en la configuracion del
 * maven-compiler-plugin del root POM: annotationProcessorPaths
 * debe incluir Lombok para que @Data, @Builder, etc. funcionen.
 *
 * Verifica:
 * - @Data: getters, setters, equals, hashCode, toString
 * - @Builder: patron Builder con metodos de construccion
 * - @NoArgsConstructor / @AllArgsConstructor: constructores
 */
class LombokAnnotationProcessingTest {

    /* ============================================================
     * @Data — getters y setters
     * ============================================================ */

    @Test
    void data_GeneraGetters() {
        LombokDataModel model = new LombokDataModel();
        model.setId(42L);
        model.setNombre("Test Libro");
        model.setCantidad(3);

        assertEquals(42L, model.getId());
        assertEquals("Test Libro", model.getNombre());
        assertEquals(3, model.getCantidad());
    }

    @Test
    void data_GeneraSetters() {
        LombokDataModel model = new LombokDataModel();
        model.setId(99L);
        model.setNombre("Otro Libro");
        model.setCantidad(7);

        assertAll(
            () -> assertEquals(99L, model.getId()),
            () -> assertEquals("Otro Libro", model.getNombre()),
            () -> assertEquals(7, model.getCantidad())
        );
    }

    /* ============================================================
     * @Data — equals y hashCode
     * ============================================================ */

    @Test
    void data_Equals_DosInstanciasConMismosValores() {
        LombokDataModel a = new LombokDataModel(1L, "Igual", 5);
        LombokDataModel b = new LombokDataModel(1L, "Igual", 5);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void data_Equals_DistintosValores() {
        LombokDataModel a = new LombokDataModel(1L, "A", 5);
        LombokDataModel b = new LombokDataModel(2L, "B", 10);

        assertNotEquals(a, b);
    }

    @Test
    void data_Equals_Null() {
        LombokDataModel model = new LombokDataModel();
        assertNotNull(model);
        assertNotEquals(null, model);
    }

    @Test
    void data_Equals_MismaInstancia() {
        LombokDataModel model = new LombokDataModel(1L, "Mismo", 1);
        assertEquals(model, model);
    }

    /* ============================================================
     * @Data — toString
     * ============================================================ */

    @Test
    void data_ToString_IncluyeCampos() {
        LombokDataModel model = new LombokDataModel(7L, "ToString Test", 42);
        String str = model.toString();

        assertAll(
            () -> assertTrue(str.contains("LombokDataModel"),
                "toString debe contener el nombre de la clase"),
            () -> assertTrue(str.contains("id=7"),
                "toString debe contener id=7"),
            () -> assertTrue(str.contains("nombre=ToString Test"),
                "toString debe contener nombre"),
            () -> assertTrue(str.contains("cantidad=42"),
                "toString debe contener cantidad")
        );
    }

    /* ============================================================
     * @Builder — patron Builder
     * ============================================================ */

    @Test
    void builder_ConstruyeObjetoConValores() {
        LombokDataModel model = LombokDataModel.builder()
            .id(10L)
            .nombre("Builder Test")
            .cantidad(100)
            .build();

        assertAll(
            () -> assertEquals(10L, model.getId()),
            () -> assertEquals("Builder Test", model.getNombre()),
            () -> assertEquals(100, model.getCantidad())
        );
    }

    @Test
    void builder_ConstructorVacioYLuegoSetters() {
        LombokDataModel model = new LombokDataModel();
        model.setId(20L);
        model.setNombre("Setters");
        model.setCantidad(200);

        assertAll(
            () -> assertEquals(20L, model.getId()),
            () -> assertEquals("Setters", model.getNombre()),
            () -> assertEquals(200, model.getCantidad())
        );
    }

    /* ============================================================
     * @NoArgsConstructor / @AllArgsConstructor
     * ============================================================ */

    @Test
    void constructor_SinArgumentos_CreaInstanciaVacia() {
        LombokDataModel model = new LombokDataModel();
        assertNull(model.getId());
        assertNull(model.getNombre());
        assertEquals(0, model.getCantidad());
    }

    @Test
    void constructor_ConTodosLosArgumentos_AsignaCorrectamente() {
        LombokDataModel model = new LombokDataModel(5L, "Full Constructor", 77);
        assertAll(
            () -> assertEquals(5L, model.getId()),
            () -> assertEquals("Full Constructor", model.getNombre()),
            () -> assertEquals(77, model.getCantidad())
        );
    }

    /* ============================================================
     * @Data — canEqual (metodo generado internamente por Lombok)
     * ============================================================ */

    @Test
    void data_CanEqual_RetornaTrueParaMismoTipo() {
        LombokDataModel a = new LombokDataModel(1L, "A", 1);
        LombokDataModel b = new LombokDataModel(1L, "A", 1);
        assertEquals(a, b);
    }
}
