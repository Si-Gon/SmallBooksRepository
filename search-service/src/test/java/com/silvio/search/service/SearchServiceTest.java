package com.silvio.search.service;

import com.silvio.search.client.CatalogClient;
import com.silvio.search.dto.LibroCatalogDTO;
import com.silvio.search.dto.SearchResultDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de SearchService.
 *
 * SearchService es básicamente un "proxy inteligente" al CatalogClient:
 * recibe la lista del catálogo, la mapea a SearchResultDTO y la devuelve.
 * Testeamos:
 *  - Que el mapeo de campos funciona correctamente
 *  - Que errores del FeignClient se convierten en ErrorConsultaCatalogoException
 *  - Que listas vacías se manejan correctamente
 */
@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private CatalogClient catalogClient;

    @InjectMocks
    private SearchService searchService;

    private LibroCatalogDTO libro(Long id, String titulo, Boolean disponible) {
        LibroCatalogDTO l = new LibroCatalogDTO();
        l.setId(id);
        l.setTitulo(titulo);
        l.setAutor("Autor Test");
        l.setIsbn("978-0-000000-00-0");
        l.setEditorial("Editorial Test");
        l.setAnioPublicacion(2023);
        l.setIdioma("Español");
        l.setGenero("Ciencia Ficción");
        l.setSinopsis("Sinopsis de prueba");
        l.setPortadaUrl("http://example.com/portada.jpg");
        l.setDisponible(disponible);
        return l;
    }

    // =====================================================================
    // obtenerTodos()
    // =====================================================================

    @Test
    void obtenerTodos_conLibros_retornaListaMapeada() {
        when(catalogClient.obtenerTodos(anyInt(), anyInt(), anyString())).thenReturn(new PageImpl<>(List.of(
            libro(1L, "Dune", true),
            libro(2L, "Fundación", false)
        )));

        List<SearchResultDTO> resultado = searchService.obtenerTodos();

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).getTitulo()).isEqualTo("Dune");
        assertThat(resultado.get(0).getDisponible()).isTrue();
        assertThat(resultado.get(1).getTitulo()).isEqualTo("Fundación");
        assertThat(resultado.get(1).getDisponible()).isFalse();

        // Verificamos que TODOS los campos se mapean (no se pierden)
        SearchResultDTO dto = resultado.get(0);
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getAutor()).isEqualTo("Autor Test");
        assertThat(dto.getIsbn()).isEqualTo("978-0-000000-00-0");
        assertThat(dto.getGenero()).isEqualTo("Ciencia Ficción");
    }

    @Test
    void obtenerTodos_pasaParametrosPaginacionCorrectos() {
        // Verifica que SearchService llame al Feign client con page=0, size=100, sort="titulo,asc"
        when(catalogClient.obtenerTodos(0, 100, "titulo,asc")).thenReturn(Page.empty());

        searchService.obtenerTodos();

        verify(catalogClient).obtenerTodos(eq(0), eq(100), eq("titulo,asc"));
    }

    @Test
    void obtenerTodos_conUnElemento_retornaUnResultado() {
        // Caso borde: page con un solo elemento
        when(catalogClient.obtenerTodos(anyInt(), anyInt(), anyString()))
            .thenReturn(new PageImpl<>(List.of(libro(1L, "Único libro", true))));

        List<SearchResultDTO> resultado = searchService.obtenerTodos();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getTitulo()).isEqualTo("Único libro");
    }

    @Test
    void obtenerTodos_consultaTotalElements() {
        // Verifica que se acceda a getTotalElements() (se usa en el log)
        Page<LibroCatalogDTO> pagina = mock(Page.class);
        when(pagina.getContent()).thenReturn(List.of(libro(1L, "Dune", true)));
        when(pagina.getTotalElements()).thenReturn(1L);
        when(catalogClient.obtenerTodos(anyInt(), anyInt(), anyString())).thenReturn(pagina);

        List<SearchResultDTO> resultado = searchService.obtenerTodos();

        assertThat(resultado).hasSize(1);
        verify(pagina).getTotalElements();
    }

    @Test
    void obtenerTodos_listaVacia_retornaListaVacia() {
        when(catalogClient.obtenerTodos(anyInt(), anyInt(), anyString())).thenReturn(Page.empty());

        List<SearchResultDTO> resultado = searchService.obtenerTodos();

        assertThat(resultado).isEmpty();
    }

    @Test
    void obtenerTodos_errorFeign_lanzaRuntimeException() {
        when(catalogClient.obtenerTodos(anyInt(), anyInt(), anyString())).thenThrow(new RuntimeException("catalog-service no disponible"));

        // El servicio ya no envuelve excepciones — se propagan directamente
        assertThatThrownBy(() -> searchService.obtenerTodos())
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("catalog-service no disponible");
    }

    // =====================================================================
    // buscarDisponibles()
    // =====================================================================

    @Test
    void buscarDisponibles_retornaSoloDisponibles() {
        when(catalogClient.obtenerDisponibles()).thenReturn(List.of(
            libro(1L, "El Quijote", true),
            libro(3L, "1984", true)
        ));

        List<SearchResultDTO> resultado = searchService.buscarDisponibles();

        assertThat(resultado).hasSize(2);
        assertThat(resultado).allMatch(d -> Boolean.TRUE.equals(d.getDisponible()));
    }

    @Test
    void buscarDisponibles_listaVacia_retornaListaVacia() {
        when(catalogClient.obtenerDisponibles()).thenReturn(List.of());

        List<SearchResultDTO> resultado = searchService.buscarDisponibles();

        assertThat(resultado).isEmpty();
    }

    @Test
    void buscarDisponibles_errorFeign_lanzaRuntimeException() {
        when(catalogClient.obtenerDisponibles())
            .thenThrow(new RuntimeException("timeout"));

        // El servicio ya no envuelve excepciones — se propagan directamente
        assertThatThrownBy(() -> searchService.buscarDisponibles())
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("timeout");
    }

    // =====================================================================
    // buscar()
    // =====================================================================

    @Test
    void buscar_porTitulo_retornaCoincidencias() {
        when(catalogClient.buscar("Dune", null, null)).thenReturn(List.of(
            libro(1L, "Dune", true)
        ));

        List<SearchResultDTO> resultado = searchService.buscar("Dune", null, null);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getTitulo()).isEqualTo("Dune");
    }

    @Test
    void buscar_sinResultados_retornaListaVacia() {
        when(catalogClient.buscar("inexistente", null, null)).thenReturn(List.of());

        List<SearchResultDTO> resultado = searchService.buscar("inexistente", null, null);

        assertThat(resultado).isEmpty();
    }

    @Test
    void buscar_sinParametros_retornaTodos() {
        when(catalogClient.buscar(null, null, null)).thenReturn(List.of(
            libro(1L, "Libro A", true),
            libro(2L, "Libro B", true)
        ));

        List<SearchResultDTO> resultado = searchService.buscar(null, null, null);

        assertThat(resultado).hasSize(2);
    }

    @Test
    void buscar_errorFeign_lanzaRuntimeException() {
        when(catalogClient.buscar(any(), any(), any()))
            .thenThrow(new RuntimeException("Connection refused"));

        // El servicio ya no envuelve excepciones — se propagan directamente
        assertThatThrownBy(() -> searchService.buscar("titulo", null, null))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Connection refused");
    }
}
