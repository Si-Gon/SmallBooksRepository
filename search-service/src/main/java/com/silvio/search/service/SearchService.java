package com.silvio.search.service;

import com.silvio.search.client.CatalogClient;
import com.silvio.search.dto.LibroCatalogDTO;
import com.silvio.search.dto.SearchResultDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final CatalogClient catalogClient;

    // Búsqueda general — delega al Catalog Service
    // El Search Service agrega valor filtrando y ordenando resultados
    
    public List<SearchResultDTO> buscar(String titulo, String autor, String genero) {
        List<LibroCatalogDTO> resultados;

        try {
            resultados = catalogClient.buscar(titulo, autor, genero);
        } catch (Exception e) {
            throw new RuntimeException("Error al consultar el catálogo: " + e.getMessage());
        }

        return resultados.stream()
                .map(this::mapearADto)
                .collect(Collectors.toList());
    }

    // Solo libros disponibles para préstamo
    
    public List<SearchResultDTO> buscarDisponibles() {
        try {
            return catalogClient.obtenerDisponibles()
                    .stream()
                    .map(this::mapearADto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Error al consultar disponibles: " + e.getMessage());
        }
    }

    // Todos los libros del catálogo
    
    public List<SearchResultDTO> obtenerTodos() {
        try {
            return catalogClient.obtenerTodos()
                    .stream()
                    .map(this::mapearADto)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Error al obtener catálogo: " + e.getMessage());
        }
    }

    private SearchResultDTO mapearADto(LibroCatalogDTO libro) {
        SearchResultDTO dto = new SearchResultDTO();
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