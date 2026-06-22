package com.silvio.catalog.service;

import com.silvio.catalog.dto.LibroRequestDTO;
import com.silvio.catalog.dto.LibroResponseDTO;
import com.silvio.catalog.model.Libro;
import com.silvio.catalog.repository.LibroRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
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
        List<Libro> libros = Arrays.asList(new Libro(), new Libro());
        when(libroRepository.findAll()).thenReturn(libros);

        // When
        List<LibroResponseDTO> result = catalogService.obtenerTodos();

        // Then
        assertEquals(2, result.size());
        verify(libroRepository).findAll();
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
        assertThrows(RuntimeException.class, () -> catalogService.obtenerPorId(999L));
        verify(libroRepository).findById(999L);
    }

    @Test
    void buscar_PorTitulo_DebeRetornarLibrosCoincidentes() {
        // Given
        List<Libro> libros = Arrays.asList(new Libro(), new Libro());
        when(libroRepository.findByTituloContainingIgnoreCase("Cien")).thenReturn(libros);

        // When
        List<LibroResponseDTO> result = catalogService.buscar("Cien", null, null);

        // Then
        assertEquals(2, result.size());
        verify(libroRepository).findByTituloContainingIgnoreCase("Cien");
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
        assertThrows(RuntimeException.class, () -> catalogService.agregar(request));
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
}
