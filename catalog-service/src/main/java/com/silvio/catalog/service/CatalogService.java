package com.silvio.catalog.service;

import com.silvio.catalog.dto.LibroRequestDTO;
import com.silvio.catalog.dto.LibroResponseDTO;
import com.silvio.catalog.model.Libro;
import com.silvio.catalog.repository.LibroRepository;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@NonNull
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogService {

    private final LibroRepository libroRepository;

    public List<LibroResponseDTO> obtenerTodos() {
        log.info("Consultando todos los libros del catálogo");
        return libroRepository.findAll()
                .stream()
                .map(this::mapearADto)
                .collect(Collectors.toList());
    }

    public List<LibroResponseDTO> obtenerDisponibles() {
        log.info("Consultando libros disponibles");
        return libroRepository.findByDisponibleTrue()
                .stream()
                .map(this::mapearADto)
                .collect(Collectors.toList());
    }

    public LibroResponseDTO obtenerPorId(@NonNull Long id) {
        log.info("Consultando libro con id: {}", id);
        Libro libro = libroRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Libro no encontrado con id: {}", id);
                    return new RuntimeException("Libro no encontrado con id: " + id);
                });
        return mapearADto(libro);
    }

    public List<LibroResponseDTO> buscar(String titulo, String autor, String genero) {
        log.info("Búsqueda — titulo: {}, autor: {}, genero: {}", titulo, autor, genero);
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
        return obtenerTodos();
    }

    public LibroResponseDTO agregar(LibroRequestDTO request) {
        log.info("Agregando libro al catálogo — ISBN: {}, título: {}", 
                request.getIsbn(), request.getTitulo());

        libroRepository.findByIsbn(request.getIsbn())
                .ifPresent(l -> {
                    log.warn("ISBN duplicado: {}", request.getIsbn());
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
        libro.setDisponible(true);

        Libro guardado = libroRepository.save(libro);
        log.info("Libro agregado exitosamente — id: {}, título: {}", 
                guardado.getId(), guardado.getTitulo());
        return mapearADto(guardado);
    }

    public LibroResponseDTO actualizar(@NonNull Long id, LibroRequestDTO request) {
        log.info("Actualizando libro id: {}", id);
        Libro libro = libroRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Libro no encontrado para actualizar — id: {}", id);
                    return new RuntimeException("Libro no encontrado con id: " + id);
                });

        libro.setTitulo(request.getTitulo());
        libro.setAutor(request.getAutor());
        libro.setIsbn(request.getIsbn());
        libro.setEditorial(request.getEditorial());
        libro.setAnioPublicacion(request.getAnioPublicacion());
        libro.setIdioma(request.getIdioma());
        libro.setGenero(request.getGenero());
        libro.setSinopsis(request.getSinopsis());
        libro.setPortadaUrl(request.getPortadaUrl());

        log.info("Libro actualizado exitosamente — id: {}", id);
        return mapearADto(libroRepository.save(libro));
    }

    public LibroResponseDTO cambiarDisponibilidad(@NonNull Long id, Boolean disponible) {
        log.info("Cambiando disponibilidad libro id: {} → {}", id, disponible);
        Libro libro = libroRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Libro no encontrado con id: " + id));
        libro.setDisponible(disponible);
        return mapearADto(libroRepository.save(libro));
    }

    public void eliminar(@NonNull Long id) {
        log.info("Eliminando libro id: {}", id);
        Libro libro = libroRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Libro no encontrado con id: " + id));
        libroRepository.delete(libro);
        log.info("Libro eliminado exitosamente — id: {}", id);
    }

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