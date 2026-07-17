package com.silvio.catalog.service;

import com.silvio.catalog.dto.LibroRequestDTO;
import com.silvio.catalog.dto.LibroResponseDTO;
import com.silvio.catalog.exception.LibroDuplicadoException;
import com.silvio.catalog.exception.LibroNotFoundException;
import com.silvio.catalog.model.Libro;
import com.silvio.catalog.repository.LibroRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CatalogServiceTest {

    @Mock
    private LibroRepository libroRepository;

    @InjectMocks
    private CatalogService catalogService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void obtenerTodos_DebeRetornarListaDeLibros() {
        // Given
        Page<Libro> page = new PageImpl<>(Arrays.asList(new Libro(), new Libro()));
        when(libroRepository.findAll(any(Pageable.class))).thenReturn(page);

        // When
        Page<LibroResponseDTO> result = catalogService.obtenerTodos(Pageable.unpaged());

        // Then
        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());
        verify(libroRepository).findAll(any(Pageable.class));
    }

    @Test
    void obtenerDisponibles_DebeRetornarSoloLibrosDisponibles() {
        // Given
        List<Libro> libros = Arrays.asList(new Libro(), new Libro());
        when(libroRepository.findByDisponibleTrue()).thenReturn(libros);

        // When
        List<LibroResponseDTO> result = catalogService.obtenerDisponibles();

        // Then
        assertEquals(2, result.size());
        verify(libroRepository).findByDisponibleTrue();
    }

    @Test
    void obtenerPorId_DebeRetornarLibroExistente() {
        // Given
        Libro libro = new Libro();
        libro.setId(1L);
        when(libroRepository.findById(1L)).thenReturn(Optional.of(libro));

        // When
        LibroResponseDTO result = catalogService.obtenerPorId(1L);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(libroRepository).findById(1L);
    }

    @Test
    void obtenerPorId_DebeLanzarExcepcionCuandoNoExiste() {
        // Given
        when(libroRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(LibroNotFoundException.class, () -> catalogService.obtenerPorId(999L));
        verify(libroRepository).findById(999L);
    }

    @Test
void buscar_PorTitulo_DebeRetornarLibrosCoincidentes() {
    // Given
    List<Libro> libros = Arrays.asList(new Libro(), new Libro());
    when(libroRepository.buscarCombinado("Cien", null, null)).thenReturn(libros);

    // When
    List<LibroResponseDTO> result = catalogService.buscar("Cien", null, null);

    // Then
    assertEquals(2, result.size());
    verify(libroRepository).buscarCombinado("Cien", null, null);
}

    @Test
    void agregar_DebeGuardarNuevoLibro() {
        // Given
        LibroRequestDTO request = new LibroRequestDTO();
        request.setTitulo("Nuevo Libro");
        request.setAutor("Autor Nuevo");
        request.setIsbn("1234567890");

        Libro libro = new Libro();
        libro.setId(1L);
        libro.setTitulo("Nuevo Libro");
        libro.setAutor("Autor Nuevo");
        libro.setIsbn("1234567890");

        when(libroRepository.findByIsbn("1234567890")).thenReturn(Optional.empty());
        when(libroRepository.save(any(Libro.class))).thenReturn(libro);

        // When
        LibroResponseDTO result = catalogService.agregar(request);

        // Then
        assertNotNull(result);
        assertEquals("Nuevo Libro", result.getTitulo());
        verify(libroRepository).findByIsbn("1234567890");
        verify(libroRepository).save(any(Libro.class));
    }

    @Test
    void agregar_DebeLanzarExcepcionCuandoISBNExiste() {
        // Given
        LibroRequestDTO request = new LibroRequestDTO();
        request.setIsbn("1234567890");

        when(libroRepository.findByIsbn("1234567890")).thenReturn(Optional.of(new Libro()));

        // When & Then
        assertThrows(LibroDuplicadoException.class, () -> catalogService.agregar(request));
        verify(libroRepository).findByIsbn("1234567890");
    }

    @Test
    void actualizar_DebeActualizarLibroExistente() {
        // Given
        LibroRequestDTO request = new LibroRequestDTO();
        request.setTitulo("Libro Actualizado");

        Libro libro = new Libro();
        libro.setId(1L);
        libro.setTitulo("Libro Original");

        when(libroRepository.findById(1L)).thenReturn(Optional.of(libro));
        when(libroRepository.save(any(Libro.class))).thenReturn(libro);

        // When
        LibroResponseDTO result = catalogService.actualizar(1L, request);

        // Then
        assertNotNull(result);
        assertEquals("Libro Actualizado", result.getTitulo());
        verify(libroRepository).findById(1L);
        verify(libroRepository).save(any(Libro.class));
    }

    @Test
    void cambiarDisponibilidad_DebeActualizarDisponibilidad() {
        // Given
        Libro libro = new Libro();
        libro.setId(1L);
        libro.setDisponible(true);

        when(libroRepository.findById(1L)).thenReturn(Optional.of(libro));
        when(libroRepository.save(any(Libro.class))).thenReturn(libro);

        // When
        LibroResponseDTO result = catalogService.cambiarDisponibilidad(1L, false);

        // Then
        assertFalse(result.getDisponible());
        verify(libroRepository).findById(1L);
        verify(libroRepository).save(any(Libro.class));
    }

    @Test
    void eliminar_DebeEliminarLibroExistente() {
        // Given
        Libro libro = new Libro();
        libro.setId(1L);

        when(libroRepository.findById(1L)).thenReturn(Optional.of(libro));

        // When
        catalogService.eliminar(1L);

        // Then
        verify(libroRepository).findById(1L);
        verify(libroRepository).delete(any(Libro.class));
    }

    // =========================================================
    // buscar() — cobertura de ramas faltantes
    // =========================================================

    @Test
    void buscar_PorAutor_DebeRetornarLibrosCoincidentes() {
        // Given
        List<Libro> libros = Arrays.asList(new Libro(), new Libro());
        when(libroRepository.buscarCombinado(null, "García", null)).thenReturn(libros);

        // When
        List<LibroResponseDTO> result = catalogService.buscar(null, "García", null);

        // Then
        assertEquals(2, result.size());
        verify(libroRepository).buscarCombinado(null, "García", null);
    }

    @Test
    void buscar_PorGenero_DebeRetornarLibrosCoincidentes() {
        // Given
        List<Libro> libros = Arrays.asList(new Libro(), new Libro());
        when(libroRepository.buscarCombinado(null, null, "Ficción")).thenReturn(libros);

        // When
        List<LibroResponseDTO> result = catalogService.buscar(null, null, "Ficción");

        // Then
        assertEquals(2, result.size());
        verify(libroRepository).buscarCombinado(null, null, "Ficción");
    }

    @Test
    void buscar_ConParametrosCombinados_DebeRetornarLibrosCoincidentes() {
        // Given
        List<Libro> libros = Arrays.asList(new Libro());
        when(libroRepository.buscarCombinado("Cien", "García", "Novela")).thenReturn(libros);

        // When
        List<LibroResponseDTO> result = catalogService.buscar("Cien", "García", "Novela");

        // Then
        assertEquals(1, result.size());
        verify(libroRepository).buscarCombinado("Cien", "García", "Novela");
    }

    @Test
    void buscar_ConTituloBlank_DebeRetornarTodos() {
        // Given
        List<Libro> libros = Arrays.asList(new Libro(), new Libro());
        // titulo blank ("  ") debe convertirse a null antes de llamar al repositorio
        when(libroRepository.buscarCombinado(null, null, null)).thenReturn(libros);

        // When
        List<LibroResponseDTO> result = catalogService.buscar("  ", null, null);

        // Then
        assertEquals(2, result.size());
        verify(libroRepository).buscarCombinado(null, null, null);
    }

    @Test
    void buscar_ConTodosNulos_DebeRetornarTodos() {
        // Given
        List<Libro> libros = Arrays.asList(new Libro(), new Libro(), new Libro());
        when(libroRepository.buscarCombinado(null, null, null)).thenReturn(libros);

        // When
        List<LibroResponseDTO> result = catalogService.buscar(null, null, null);

        // Then
        assertEquals(3, result.size());
        verify(libroRepository).buscarCombinado(null, null, null);
    }

    @Test
    void buscar_SinResultados_DebeRetornarListaVacia() {
        // Given
        when(libroRepository.buscarCombinado("XYZ", null, null)).thenReturn(Collections.emptyList());

        // When
        List<LibroResponseDTO> result = catalogService.buscar("XYZ", null, null);

        // Then
        assertTrue(result.isEmpty());
        verify(libroRepository).buscarCombinado("XYZ", null, null);
    }

    // =========================================================
    // actualizar() — excepción cuando no existe
    // =========================================================

    @Test
    void actualizar_DebeLanzarExcepcionCuandoNoExiste() {
        // Given
        when(libroRepository.findById(999L)).thenReturn(Optional.empty());
        LibroRequestDTO request = new LibroRequestDTO();

        // When & Then
        assertThrows(LibroNotFoundException.class, () -> catalogService.actualizar(999L, request));
        verify(libroRepository).findById(999L);
        verify(libroRepository, never()).save(any());
    }

    // =========================================================
    // cambiarDisponibilidad() — excepción cuando no existe
    // =========================================================

    @Test
    void cambiarDisponibilidad_DebeLanzarExcepcionCuandoNoExiste() {
        // Given
        when(libroRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(LibroNotFoundException.class, () -> catalogService.cambiarDisponibilidad(999L, false));
        verify(libroRepository).findById(999L);
        verify(libroRepository, never()).save(any());
    }

    // =========================================================
    // eliminar() — excepción cuando no existe
    // =========================================================

    @Test
    void eliminar_DebeLanzarExcepcionCuandoNoExiste() {
        // Given
        when(libroRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(LibroNotFoundException.class, () -> catalogService.eliminar(999L));
        verify(libroRepository).findById(999L);
        verify(libroRepository, never()).delete(any());
    }

    // =========================================================
    // obtenerTodos() / obtenerDisponibles() — listas vacías
    // =========================================================

    @Test
    void obtenerTodos_CuandoNoHayLibros_DebeRetornarListaVacia() {
        // Given
        Page<Libro> page = new PageImpl<>(Collections.emptyList());
        when(libroRepository.findAll(any(Pageable.class))).thenReturn(page);

        // When
        Page<LibroResponseDTO> result = catalogService.obtenerTodos(Pageable.unpaged());

        // Then
        assertTrue(result.isEmpty());
        verify(libroRepository).findAll(any(Pageable.class));
    }

    // =========================================================
    // obtenerTodos() — paginación con PageRequest
    // =========================================================

    @Test
    void obtenerTodos_ConPageRequest_DebeRetornarPaginaConMetadatos() {
        // Given: 5 libros en total, página de tamaño 2
        List<Libro> todos = Arrays.asList(new Libro(), new Libro(), new Libro(), new Libro(), new Libro());
        Page<Libro> page = new PageImpl<>(todos.subList(0, 2), PageRequest.of(0, 2), 5);
        when(libroRepository.findAll(any(Pageable.class))).thenReturn(page);

        // When
        Page<LibroResponseDTO> result = catalogService.obtenerTodos(PageRequest.of(0, 2));

        // Then: verificar metadatos de paginación
        assertEquals(2, result.getSize());
        assertEquals(0, result.getNumber());
        assertEquals(5, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
        assertTrue(result.isFirst());
        assertFalse(result.isLast());
        verify(libroRepository).findAll(any(Pageable.class));
    }

    @Test
    void obtenerTodos_ConPageRequestSegundaPagina_DebeRetornarPaginaCorrecta() {
        // Given: 5 libros en total, solicitamos página 2 (índice 1) de tamaño 2
        List<Libro> todos = Arrays.asList(new Libro(), new Libro(), new Libro(), new Libro(), new Libro());
        Page<Libro> page = new PageImpl<>(todos.subList(2, 4), PageRequest.of(1, 2), 5);
        when(libroRepository.findAll(any(Pageable.class))).thenReturn(page);

        // When
        Page<LibroResponseDTO> result = catalogService.obtenerTodos(PageRequest.of(1, 2));

        // Then
        assertEquals(2, result.getContent().size());
        assertEquals(1, result.getNumber());
        assertEquals(5, result.getTotalElements());
        assertFalse(result.isFirst());
        assertFalse(result.isLast());
        verify(libroRepository).findAll(any(Pageable.class));
    }

    @Test
    void obtenerTodos_ConSort_DebePasarSortAlRepositorio() {
        // Given
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "autor"));
        Page<Libro> page = new PageImpl<>(Arrays.asList(new Libro(), new Libro()));
        when(libroRepository.findAll(pageable)).thenReturn(page);

        // When
        Page<LibroResponseDTO> result = catalogService.obtenerTodos(pageable);

        // Then
        assertEquals(2, result.getTotalElements());
        verify(libroRepository).findAll(pageable);
        // Verificar que el Pageable contenía el sort esperado
        assertEquals(Sort.by(Sort.Direction.DESC, "autor"), pageable.getSort());
    }

    @Test
    void obtenerTodos_ConPaginaFueraDeRango_DebeRetornarVacio() {
        // Given: 3 libros, solicitamos página 10 (índice 10) de tamaño 5 — fuera de rango
        Page<Libro> page = new PageImpl<>(Collections.emptyList(), PageRequest.of(10, 5), 3);
        when(libroRepository.findAll(any(Pageable.class))).thenReturn(page);

        // When
        Page<LibroResponseDTO> result = catalogService.obtenerTodos(PageRequest.of(10, 5));

        // Then
        assertTrue(result.isEmpty());
        assertEquals(0, result.getNumberOfElements());
        assertEquals(3, result.getTotalElements());
        verify(libroRepository).findAll(any(Pageable.class));
    }

    @Test
    void obtenerDisponibles_CuandoNoHayDisponibles_DebeRetornarListaVacia() {
        // Given
        when(libroRepository.findByDisponibleTrue()).thenReturn(Collections.emptyList());

        // When
        List<LibroResponseDTO> result = catalogService.obtenerDisponibles();

        // Then
        assertTrue(result.isEmpty());
        verify(libroRepository).findByDisponibleTrue();
    }

    // =========================================================
    // buscar() — parámetros blank → null (rama faltante)
    // =========================================================

    @Test
    void buscar_ConAutorBlank_DebeConvertirANull() {
        // Given
        // autor "  " debe convertirse a null antes de llamar al repositorio
        List<Libro> libros = Arrays.asList(new Libro());
        when(libroRepository.buscarCombinado("Cien", null, null)).thenReturn(libros);

        // When
        List<LibroResponseDTO> result = catalogService.buscar("Cien", "  ", null);

        // Then
        assertEquals(1, result.size());
        verify(libroRepository).buscarCombinado("Cien", null, null);
    }

    @Test
    void buscar_ConGeneroBlank_DebeConvertirANull() {
        // Given
        List<Libro> libros = Arrays.asList(new Libro());
        when(libroRepository.buscarCombinado(null, "García", null)).thenReturn(libros);

        // When
        List<LibroResponseDTO> result = catalogService.buscar(null, "García", "  ");

        // Then
        assertEquals(1, result.size());
        verify(libroRepository).buscarCombinado(null, "García", null);
    }

    @Test
    void buscar_ConAutorBlankYGeneroBlank_DebeConvertirAmbosANull() {
        // Given
        List<Libro> libros = Arrays.asList(new Libro(), new Libro());
        when(libroRepository.buscarCombinado(null, null, null)).thenReturn(libros);

        // When: autor y genero blank → ambos deben ir como null
        List<LibroResponseDTO> result = catalogService.buscar(null, "", "");

        // Then
        assertEquals(2, result.size());
        verify(libroRepository).buscarCombinado(null, null, null);
    }

    // =========================================================
    // agregar() — mapeo completo de todos los campos
    // =========================================================

    @Test
    void agregar_ConDatosCompletos_DebeMapearTodosLosCampos() {
        // Given
        LibroRequestDTO request = new LibroRequestDTO();
        request.setTitulo("Cien años de soledad");
        request.setAutor("Gabriel García Márquez");
        request.setIsbn("9788437604947");
        request.setEditorial("Editorial Sudamericana");
        request.setAnioPublicacion(1967);
        request.setIdioma("Español");
        request.setGenero("Realismo mágico");
        request.setSinopsis("Novela que narra la historia de la familia Buendía en Macondo.");
        request.setPortadaUrl("https://example.com/portada.jpg");

        Libro libroGuardado = new Libro();
        libroGuardado.setId(1L);
        libroGuardado.setTitulo("Cien años de soledad");
        libroGuardado.setAutor("Gabriel García Márquez");
        libroGuardado.setIsbn("9788437604947");
        libroGuardado.setEditorial("Editorial Sudamericana");
        libroGuardado.setAnioPublicacion(1967);
        libroGuardado.setIdioma("Español");
        libroGuardado.setGenero("Realismo mágico");
        libroGuardado.setSinopsis("Novela que narra la historia de la familia Buendía en Macondo.");
        libroGuardado.setPortadaUrl("https://example.com/portada.jpg");
        libroGuardado.setDisponible(true);

        when(libroRepository.findByIsbn("9788437604947")).thenReturn(Optional.empty());
        when(libroRepository.save(any(Libro.class))).thenReturn(libroGuardado);

        // When
        LibroResponseDTO result = catalogService.agregar(request);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Cien años de soledad", result.getTitulo());
        assertEquals("Gabriel García Márquez", result.getAutor());
        assertEquals("9788437604947", result.getIsbn());
        assertEquals("Editorial Sudamericana", result.getEditorial());
        assertEquals(1967, result.getAnioPublicacion());
        assertEquals("Español", result.getIdioma());
        assertEquals("Realismo mágico", result.getGenero());
        assertEquals("Novela que narra la historia de la familia Buendía en Macondo.", result.getSinopsis());
        assertEquals("https://example.com/portada.jpg", result.getPortadaUrl());
        assertTrue(result.getDisponible());

        verify(libroRepository).findByIsbn("9788437604947");
        verify(libroRepository).save(any(Libro.class));
    }

    // =========================================================
    // actualizar() — mapeo completo de todos los campos
    // =========================================================

    @Test
    void actualizar_ConDatosCompletos_DebeMapearTodosLosCampos() {
        // Given
        LibroRequestDTO request = new LibroRequestDTO();
        request.setTitulo("Cien años de soledad");
        request.setAutor("Gabriel García Márquez");
        request.setIsbn("9788437604947");
        request.setEditorial("Editorial Sudamericana");
        request.setAnioPublicacion(1967);
        request.setIdioma("Español");
        request.setGenero("Realismo mágico");
        request.setSinopsis("Novela que narra la historia de la familia Buendía en Macondo.");
        request.setPortadaUrl("https://example.com/portada.jpg");

        Libro libroExistente = new Libro();
        libroExistente.setId(1L);
        libroExistente.setTitulo("Título Original");
        libroExistente.setAutor("Autor Original");
        libroExistente.setIsbn("0000000000000");
        libroExistente.setEditorial("Original");
        libroExistente.setAnioPublicacion(1900);
        libroExistente.setIdioma("Inglés");
        libroExistente.setGenero("Suspenso");
        libroExistente.setSinopsis("Sinopsis original.");
        libroExistente.setPortadaUrl("https://original.com/img.jpg");

        Libro libroActualizado = new Libro();
        libroActualizado.setId(1L);
        libroActualizado.setTitulo("Cien años de soledad");
        libroActualizado.setAutor("Gabriel García Márquez");
        libroActualizado.setIsbn("9788437604947");
        libroActualizado.setEditorial("Editorial Sudamericana");
        libroActualizado.setAnioPublicacion(1967);
        libroActualizado.setIdioma("Español");
        libroActualizado.setGenero("Realismo mágico");
        libroActualizado.setSinopsis("Novela que narra la historia de la familia Buendía en Macondo.");
        libroActualizado.setPortadaUrl("https://example.com/portada.jpg");
        libroActualizado.setDisponible(true);

        when(libroRepository.findById(1L)).thenReturn(Optional.of(libroExistente));
        when(libroRepository.save(any(Libro.class))).thenReturn(libroActualizado);

        // When
        LibroResponseDTO result = catalogService.actualizar(1L, request);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Cien años de soledad", result.getTitulo());
        assertEquals("Gabriel García Márquez", result.getAutor());
        assertEquals("9788437604947", result.getIsbn());
        assertEquals("Editorial Sudamericana", result.getEditorial());
        assertEquals(1967, result.getAnioPublicacion());
        assertEquals("Español", result.getIdioma());
        assertEquals("Realismo mágico", result.getGenero());
        assertEquals("Novela que narra la historia de la familia Buendía en Macondo.", result.getSinopsis());
        assertEquals("https://example.com/portada.jpg", result.getPortadaUrl());
        assertTrue(result.getDisponible());

        verify(libroRepository).findById(1L);
        verify(libroRepository).save(any(Libro.class));
    }

    // =========================================================
    // obtenerPorId() — null id (ramas @NonNull)
    // =========================================================

    @Test
    void obtenerPorId_ConIdNulo_DebeLanzarExcepcion() {
        // When & Then
        assertThrows(NullPointerException.class, () -> catalogService.obtenerPorId(null));
        verify(libroRepository, never()).findById(any());
    }

    // =========================================================
    // actualizar() — null id (rama @NonNull)
    // =========================================================

    @Test
    void actualizar_ConIdNulo_DebeLanzarExcepcion() {
        // Given
        LibroRequestDTO request = new LibroRequestDTO();

        // When & Then
        assertThrows(NullPointerException.class, () -> catalogService.actualizar(null, request));
        verify(libroRepository, never()).findById(any());
        verify(libroRepository, never()).save(any());
    }

    // =========================================================
    // cambiarDisponibilidad() — null id (rama @NonNull)
    // =========================================================

    @Test
    void cambiarDisponibilidad_ConIdNulo_DebeLanzarExcepcion() {
        // When & Then
        assertThrows(NullPointerException.class, () -> catalogService.cambiarDisponibilidad(null, true));
        verify(libroRepository, never()).findById(any());
        verify(libroRepository, never()).save(any());
    }

    // =========================================================
    // eliminar() — null id (rama @NonNull)
    // =========================================================

    @Test
    void eliminar_ConIdNulo_DebeLanzarExcepcion() {
        // When & Then
        assertThrows(NullPointerException.class, () -> catalogService.eliminar(null));
        verify(libroRepository, never()).findById(any());
        verify(libroRepository, never()).delete(any());
    }

    // =========================================================
    // @Transactional — verificación de rollback ante excepción
    // =========================================================

    @Test
    void agregar_CuandoRepositoryFalla_DebePropagarExcepcion() {
        // Given
        LibroRequestDTO request = new LibroRequestDTO();
        request.setIsbn("9788437604947");
        request.setTitulo("Libro Test");

        when(libroRepository.findByIsbn("9788437604947")).thenReturn(Optional.empty());
        when(libroRepository.save(any(Libro.class))).thenThrow(new RuntimeException("Error de persistencia"));

        // When & Then — la excepción se propaga, @Transactional realizaría rollback
        assertThrows(RuntimeException.class, () -> catalogService.agregar(request));
        verify(libroRepository).save(any(Libro.class));
    }

    @Test
    void actualizar_CuandoRepositoryFalla_DebePropagarExcepcion() {
        // Given
        LibroRequestDTO request = new LibroRequestDTO();
        request.setTitulo("Libro Actualizado");

        Libro libro = new Libro();
        libro.setId(1L);

        when(libroRepository.findById(1L)).thenReturn(Optional.of(libro));
        when(libroRepository.save(any(Libro.class))).thenThrow(new RuntimeException("Error de persistencia"));

        // When & Then — la excepción se propaga, @Transactional realizaría rollback
        assertThrows(RuntimeException.class, () -> catalogService.actualizar(1L, request));
        verify(libroRepository).save(any(Libro.class));
    }

    @Test
    void cambiarDisponibilidad_CuandoRepositoryFalla_DebePropagarExcepcion() {
        // Given
        Libro libro = new Libro();
        libro.setId(1L);
        libro.setDisponible(true);

        when(libroRepository.findById(1L)).thenReturn(Optional.of(libro));
        when(libroRepository.save(any(Libro.class))).thenThrow(new RuntimeException("Error de persistencia"));

        // When & Then — la excepción se propaga, @Transactional realizaría rollback
        assertThrows(RuntimeException.class, () -> catalogService.cambiarDisponibilidad(1L, false));
        verify(libroRepository).save(any(Libro.class));
    }

    @Test
    void eliminar_CuandoRepositoryFalla_DebePropagarExcepcion() {
        // Given
        Libro libro = new Libro();
        libro.setId(1L);

        when(libroRepository.findById(1L)).thenReturn(Optional.of(libro));
        doThrow(new RuntimeException("Error de persistencia")).when(libroRepository).delete(any(Libro.class));

        // When & Then — la excepción se propaga, @Transactional realizaría rollback
        assertThrows(RuntimeException.class, () -> catalogService.eliminar(1L));
        verify(libroRepository).delete(any(Libro.class));
    }

    // =========================================================
    // @Transactional — verificación de anotación en métodos de escritura
    // =========================================================

    @Test
    void agregar_DebeTenerAnotacionTransactional() throws NoSuchMethodException {
        // Given — método de escritura
        var metodo = CatalogService.class.getMethod("agregar", LibroRequestDTO.class);

        // When — verificar que tiene @Transactional sin readOnly
        var anotacion = metodo.getAnnotation(Transactional.class);

        // Then
        assertNotNull(anotacion, "agregar() debe tener @Transactional");
        assertFalse(anotacion.readOnly(), "agregar() no debe ser readOnly");
    }

    @Test
    void actualizar_DebeTenerAnotacionTransactional() throws NoSuchMethodException {
        // Given
        var metodo = CatalogService.class.getMethod("actualizar", Long.class, LibroRequestDTO.class);

        // When
        var anotacion = metodo.getAnnotation(Transactional.class);

        // Then
        assertNotNull(anotacion, "actualizar() debe tener @Transactional");
        assertFalse(anotacion.readOnly(), "actualizar() no debe ser readOnly");
    }

    @Test
    void cambiarDisponibilidad_DebeTenerAnotacionTransactional() throws NoSuchMethodException {
        // Given
        var metodo = CatalogService.class.getMethod("cambiarDisponibilidad", Long.class, Boolean.class);

        // When
        var anotacion = metodo.getAnnotation(Transactional.class);

        // Then
        assertNotNull(anotacion, "cambiarDisponibilidad() debe tener @Transactional");
        assertFalse(anotacion.readOnly(), "cambiarDisponibilidad() no debe ser readOnly");
    }

    @Test
    void eliminar_DebeTenerAnotacionTransactional() throws NoSuchMethodException {
        // Given
        var metodo = CatalogService.class.getMethod("eliminar", Long.class);

        // When
        var anotacion = metodo.getAnnotation(Transactional.class);

        // Then
        assertNotNull(anotacion, "eliminar() debe tener @Transactional");
        assertFalse(anotacion.readOnly(), "eliminar() no debe ser readOnly");
    }

    @Test
    void metodosLectura_NoDebenTenerTransactionalSinReadOnly() throws NoSuchMethodException {
        // Given — métodos de lectura que ya tenían @Transactional(readOnly = true)
        var metodosLectura = Map.of(
                "obtenerTodos", new Class[]{Pageable.class},
                "obtenerDisponibles", new Class[]{},
                "obtenerPorId", new Class[]{Long.class},
                "buscar", new Class[]{String.class, String.class, String.class}
        );

        // When & Then — cada método de lectura debe tener readOnly = true
        for (var entry : metodosLectura.entrySet()) {
            var metodo = CatalogService.class.getMethod(entry.getKey(), entry.getValue());
            var anotacion = metodo.getAnnotation(Transactional.class);
            assertNotNull(anotacion, entry.getKey() + "() debe tener @Transactional");
            assertTrue(anotacion.readOnly(), entry.getKey() + "() debe ser readOnly = true");
        }
    }
}
