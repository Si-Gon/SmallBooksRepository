package com.silvio.catalog.repository;

import com.silvio.catalog.model.Libro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de integración ligera para LibroRepository usando @DataJpaTest.
 *
 * ¿Qué es @DataJpaTest?
 * Levanta solo la capa de persistencia (JPA, Hibernate, DataSource) sin
 * arrancar el servidor web ni otros servicios. Cada test es transaccional
 * y se revierte al final, por lo que no contaminan la base de datos.
 *
 * Usa H2 en memoria (configurado en application-test.yml) que replica
 * el esquema MySQL a partir de las entidades JPA gracias a ddl-auto=create-drop.
 *
 * Flyway está desactivado en el perfil test (flyway.enabled=false).
 */
@DataJpaTest
@ActiveProfiles("test")
class LibroRepositoryTest {

    @Autowired
    private LibroRepository libroRepository;

    private Libro libro1;
    private Libro libro2;
    private Libro libro3;

    @BeforeEach
    void setUp() {
        libroRepository.deleteAll();

        // Libro 1: Cien años de soledad — García Márquez — Realismo mágico — disponible
        libro1 = new Libro();
        libro1.setTitulo("Cien años de soledad");
        libro1.setAutor("Gabriel García Márquez");
        libro1.setIsbn("9788437604947");
        libro1.setEditorial("Sudamericana");
        libro1.setAnioPublicacion(1967);
        libro1.setIdioma("Español");
        libro1.setGenero("Realismo mágico");
        libro1.setSinopsis("Historia de la familia Buendía en Macondo.");
        libro1.setPortadaUrl("https://example.com/cien-anios.jpg");
        libro1.setDisponible(true);

        // Libro 2: El Quijote — Cervantes — Novela — disponible
        libro2 = new Libro();
        libro2.setTitulo("El ingenioso hidalgo Don Quijote de la Mancha");
        libro2.setAutor("Miguel de Cervantes Saavedra");
        libro2.setIsbn("9788420427111");
        libro2.setEditorial("Alfaguara");
        libro2.setAnioPublicacion(1605);
        libro2.setIdioma("Español");
        libro2.setGenero("Novela");
        libro2.setSinopsis("Aventuras de un hidalgo que enloquece leyendo libros de caballerías.");
        libro2.setPortadaUrl("https://example.com/quijote.jpg");
        libro2.setDisponible(true);

        // Libro 3: The Great Gatsby — Fitzgerald — Novela — NO disponible
        libro3 = new Libro();
        libro3.setTitulo("The Great Gatsby");
        libro3.setAutor("F. Scott Fitzgerald");
        libro3.setIsbn("9780743273565");
        libro3.setEditorial("Scribner");
        libro3.setAnioPublicacion(1925);
        libro3.setIdioma("Inglés");
        libro3.setGenero("Novela");
        libro3.setSinopsis("Historia del misterioso Jay Gatsby en los años 20.");
        libro3.setPortadaUrl("https://example.com/gatsby.jpg");
        libro3.setDisponible(false);

        libroRepository.save(libro1);
        libroRepository.save(libro2);
        libroRepository.save(libro3);
    }

    // =========================================================
    // buscarCombinado — custom @Query JPQL
    // =========================================================

    @Test
    void buscarCombinado_PorTitulo_DebeRetornarLibrosCoincidentes() {
        // When: búsqueda por título parcial (case-insensitive)
        List<Libro> resultados = libroRepository.buscarCombinado("Cien", null, null);

        // Then
        assertEquals(1, resultados.size());
        assertEquals("Cien años de soledad", resultados.get(0).getTitulo());
    }

    @Test
    void buscarCombinado_PorTitulo_ConMinusculas_DebeSerCaseInsensitive() {
        // When: búsqueda en minúsculas
        List<Libro> resultados = libroRepository.buscarCombinado("el ingenioso", null, null);

        // Then
        assertEquals(1, resultados.size());
        assertEquals("El ingenioso hidalgo Don Quijote de la Mancha", resultados.get(0).getTitulo());
    }

    @Test
    void buscarCombinado_PorAutor_DebeRetornarLibrosCoincidentes() {
        // When: búsqueda por autor parcial
        List<Libro> resultados = libroRepository.buscarCombinado(null, "García", null);

        // Then
        assertEquals(1, resultados.size());
        assertEquals("Gabriel García Márquez", resultados.get(0).getAutor());
    }

    @Test
    void buscarCombinado_PorGenero_DebeRetornarLibrosCoincidentes() {
        // When: búsqueda por género exacto (case-insensitive)
        List<Libro> resultados = libroRepository.buscarCombinado(null, null, "Novela");

        // Then: libro2 y libro3 tienen género "Novela"
        assertEquals(2, resultados.size());
    }

    @Test
    void buscarCombinado_PorGenero_ConMinusculas_DebeSerCaseInsensitive() {
        // When: género en minúsculas
        List<Libro> resultados = libroRepository.buscarCombinado(null, null, "realismo mágico");

        // Then
        assertEquals(1, resultados.size());
        assertEquals("Cien años de soledad", resultados.get(0).getTitulo());
    }

    @Test
    void buscarCombinado_ConTodosParametros_DebeRetornarResultados() {
        // When: búsqueda combinada
        List<Libro> resultados = libroRepository.buscarCombinado("Cien", "García Márquez", "Realismo mágico");

        // Then
        assertEquals(1, resultados.size());
        assertEquals("Cien años de soledad", resultados.get(0).getTitulo());
    }

    @Test
    void buscarCombinado_SinParametros_DebeRetornarTodos() {
        // When: todos los parámetros null
        List<Libro> resultados = libroRepository.buscarCombinado(null, null, null);

        // Then: debe retornar todos los libros
        assertEquals(3, resultados.size());
    }

    @Test
    void buscarCombinado_SinResultados_DebeRetornarListaVacia() {
        // When: búsqueda sin coincidencias
        List<Libro> resultados = libroRepository.buscarCombinado("XYZNoExiste", null, null);

        // Then
        assertTrue(resultados.isEmpty());
    }

    // =========================================================
    // findByIsbn
    // =========================================================

    @Test
    void findByIsbn_CuandoExiste_DebeRetornarLibro() {
        // When
        Optional<Libro> resultado = libroRepository.findByIsbn("9788437604947");

        // Then
        assertTrue(resultado.isPresent());
        assertEquals("Cien años de soledad", resultado.get().getTitulo());
    }

    @Test
    void findByIsbn_CuandoNoExiste_DebeRetornarVacio() {
        // When
        Optional<Libro> resultado = libroRepository.findByIsbn("0000000000000");

        // Then
        assertFalse(resultado.isPresent());
    }

    // =========================================================
    // findByDisponibleTrue
    // =========================================================

    @Test
    void findByDisponibleTrue_DebeRetornarSoloDisponibles() {
        // When
        List<Libro> resultados = libroRepository.findByDisponibleTrue();

        // Then: libro1 y libro2 son disponibles, libro3 no
        assertEquals(2, resultados.size());
        assertTrue(resultados.stream().allMatch(Libro::getDisponible));
    }

    // =========================================================
    // findByTituloContainingIgnoreCase
    // =========================================================

    @Test
    void findByTituloContainingIgnoreCase_DebeRetornarCoincidentes() {
        // When
        List<Libro> resultados = libroRepository.findByTituloContainingIgnoreCase("quijote");

        // Then: case-insensitive, contiene "quijote"
        assertEquals(1, resultados.size());
        assertTrue(resultados.get(0).getTitulo().toLowerCase().contains("quijote"));
    }

    // =========================================================
    // findByAutorContainingIgnoreCase
    // =========================================================

    @Test
    void findByAutorContainingIgnoreCase_DebeRetornarCoincidentes() {
        // When
        List<Libro> resultados = libroRepository.findByAutorContainingIgnoreCase("cervantes");

        // Then
        assertEquals(1, resultados.size());
        assertEquals("Miguel de Cervantes Saavedra", resultados.get(0).getAutor());
    }

    // =========================================================
    // findByGeneroIgnoreCase
    // =========================================================

    @Test
    void findByGeneroIgnoreCase_DebeRetornarCoincidentes() {
        // When
        List<Libro> resultados = libroRepository.findByGeneroIgnoreCase("novela");

        // Then: case-insensitive, libro2 y libro3
        assertEquals(2, resultados.size());
    }

    // =========================================================
    // Integridad de datos
    // =========================================================

    @Test
    void findAll_DebeRetornarTodosLosLibros() {
        // When
        List<Libro> resultados = libroRepository.findAll();

        // Then
        assertEquals(3, resultados.size());
    }

    @Test
    void isbnUnico_DebeRechazarISBNDuplicado() {
        // Given: intentamos guardar un libro con ISBN ya existente
        Libro duplicado = new Libro();
        duplicado.setTitulo("Libro duplicado");
        duplicado.setAutor("Autor");
        duplicado.setIsbn("9788437604947"); // mismo ISBN que libro1
        duplicado.setDisponible(true);

        // When & Then: debe lanzar excepción por unique constraint
        assertThrows(Exception.class, () -> libroRepository.saveAndFlush(duplicado));
    }

    @Test
    void eliminar_Libro_DebeReflejarseEnConsulta() {
        // Given
        assertEquals(3, libroRepository.count());

        // When: eliminamos libro1
        libroRepository.delete(libro1);

        // Then
        assertEquals(2, libroRepository.count());
        assertFalse(libroRepository.findById(libro1.getId()).isPresent());
    }
}
