package com.silvio.catalog.service;

import com.silvio.catalog.dto.LibroRequestDTO;
import com.silvio.catalog.dto.LibroResponseDTO;
import com.silvio.catalog.model.Libro;
import com.silvio.catalog.repository.LibroRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final LibroRepository libroRepository;

    
    // GET todos los libros

    public List<LibroResponseDTO> obtenerTodos() {
        return libroRepository.findAll()
                .stream()
                .map(this::mapearADto)
                .collect(Collectors.toList());
    }

    // GET solo disponibles — para mostrar qué se puede prestar
   
    public List<LibroResponseDTO> obtenerDisponibles() {
        return libroRepository.findByDisponibleTrue()
                .stream()
                .map(this::mapearADto)
                .collect(Collectors.toList());
    }

    // GET por id
   
    public LibroResponseDTO obtenerPorId(Long id) {
        Libro libro = libroRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Libro no encontrado con id: " + id));
        return mapearADto(libro);
    }

    // Búsqueda por título, autor o género
    
    public List<LibroResponseDTO> buscar(String titulo, String autor, String genero) {
        if (titulo != null && !titulo.isBlank()) {
            return libroRepository.findByTituloContainingIgnoreCase(titulo)
                    .stream().map(this::mapearADto).collect(Collectors.toList());
        }
        if (autor != null && !autor.isBlank()) {
            return libroRepository.findByAutorContainingIgnoreCase(autor)
                    .stream().map(this::mapearADto).collect(Collectors.toList());
        }
        if (genero != null && !genero.isBlank()) {
            return libroRepository.findByGeneroIgnoreCase(genero)
                    .stream().map(this::mapearADto).collect(Collectors.toList());
        }
        // Si no se especifica ningún filtro devuelve todos
        return obtenerTodos();
    }

    // POST — agregar libro al catálogo
  
    public LibroResponseDTO agregar(LibroRequestDTO request) {

        // Verificar ISBN duplicado
        libroRepository.findByIsbn(request.getIsbn())
                .ifPresent(l -> {
                    throw new RuntimeException("Ya existe un libro con ISBN: " + request.getIsbn());
                });

        Libro libro = new Libro();
        libro.setTitulo(request.getTitulo());
        libro.setAutor(request.getAutor());
        libro.setIsbn(request.getIsbn());
        libro.setEditorial(request.getEditorial());
        libro.setAnioPublicacion(request.getAnioPublicacion());
        libro.setIdioma(request.getIdioma());
        libro.setGenero(request.getGenero());
        libro.setSinopsis(request.getSinopsis());
        libro.setPortadaUrl(request.getPortadaUrl());
        libro.setDisponible(true); // todo libro nuevo nace disponible

        return mapearADto(libroRepository.save(libro));
    }

    // PUT — actualizar datos del libro
    
    public LibroResponseDTO actualizar(Long id, LibroRequestDTO request) {
        Libro libro = libroRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Libro no encontrado con id: " + id));

        libro.setTitulo(request.getTitulo());
        libro.setAutor(request.getAutor());
        libro.setIsbn(request.getIsbn());
        libro.setEditorial(request.getEditorial());
        libro.setAnioPublicacion(request.getAnioPublicacion());
        libro.setIdioma(request.getIdioma());
        libro.setGenero(request.getGenero());
        libro.setSinopsis(request.getSinopsis());
        libro.setPortadaUrl(request.getPortadaUrl());

        return mapearADto(libroRepository.save(libro));
    }

    // PATCH — cambiar disponibilidad
    // Llamado por E-Lending Service via Feign cuando se crea o cierra un préstamo
    
    public LibroResponseDTO cambiarDisponibilidad(Long id, Boolean disponible) {
        Libro libro = libroRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Libro no encontrado con id: " + id));

        libro.setDisponible(disponible);
        return mapearADto(libroRepository.save(libro));
    }

    // DELETE — eliminar libro del catálogo
    
    public void eliminar(Long id) {
        Libro libro = libroRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Libro no encontrado con id: " + id));
        libroRepository.delete(libro);
    }

    // Mapeo privado Entidad → ResponseDTO
    
    private LibroResponseDTO mapearADto(Libro libro) {
        LibroResponseDTO dto = new LibroResponseDTO();
        dto.setId(libro.getId());
        dto.setTitulo(libro.getTitulo());
        dto.setAutor(libro.getAutor());
        dto.setIsbn(libro.getIsbn());
        dto.setEditorial(libro.getEditorial());
        dto.setAnioPublicacion(libro.getAnioPublicacion());
        dto.setIdioma(libro.getIdioma());
        dto.setGenero(libro.getGenero());
        dto.setSinopsis(libro.getSinopsis());
        dto.setPortadaUrl(libro.getPortadaUrl());
        dto.setDisponible(libro.getDisponible());
        return dto;
    }
}