package com.silvio.search.service;

import com.silvio.search.client.CatalogClient;
import com.silvio.search.dto.LibroCatalogDTO;
import com.silvio.search.dto.SearchResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import io.micrometer.observation.annotation.Observed;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final CatalogClient catalogClient;

    @Observed(name = "search.buscar")
    public List<SearchResultDTO> buscar(String titulo, String autor, String genero) {
        log.info("Búsqueda — titulo: {}, autor: {}, genero: {}", titulo, autor, genero);
        List<LibroCatalogDTO> resultados = catalogClient.buscar(titulo, autor, genero);
        log.info("Resultados encontrados: {}", resultados.size());
        return resultados.stream().map(this::mapearADto).collect(Collectors.toList());
    }

    @Observed(name = "search.buscarDisponibles")
    public List<SearchResultDTO> buscarDisponibles() {
        log.info("Consultando libros disponibles via Catalog Service");
        List<LibroCatalogDTO> resultados = catalogClient.obtenerDisponibles();
        log.info("Libros disponibles encontrados: {}", resultados.size());
        return resultados.stream().map(this::mapearADto).collect(Collectors.toList());
    }

    @Observed(name = "search.obtenerTodos")
    public List<SearchResultDTO> obtenerTodos() {
        log.info("Consultando catálogo completo via Catalog Service (paginado)");
        Page<LibroCatalogDTO> pagina = catalogClient.obtenerTodos(0, 100, "titulo,asc");
        List<LibroCatalogDTO> resultados = pagina.getContent();
        log.info("Total libros en catálogo: {}", pagina.getTotalElements());
        return resultados.stream().map(this::mapearADto).collect(Collectors.toList());
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