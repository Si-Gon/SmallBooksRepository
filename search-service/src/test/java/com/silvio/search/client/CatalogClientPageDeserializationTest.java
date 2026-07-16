package com.silvio.search.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvio.search.dto.LibroCatalogDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests de deserialización JSON de Page<LibroCatalogDTO>.
 * Verifica que Jackson (usado por Feign) pueda deserializar
 * la respuesta paginada de catalog-service en distintos escenarios.
 *
 * Usamos JSON predefinido que replica el formato que produce
 * catalog-service (Page con PageRequest con Sort y contenido real).
 */
@SpringBootTest
@ActiveProfiles("test")
class CatalogClientPageDeserializationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private static final TypeReference<Page<LibroCatalogDTO>> PAGE_TYPE =
            new TypeReference<Page<LibroCatalogDTO>>() {};

    @Test
    void deserializaPaginaConDosLibros() throws Exception {
        String json = """
            {
              "content": [
                {"id":1,"titulo":"Dune","autor":"Frank Herbert","isbn":"978-0-441-17271-9",
                 "editorial":"Ace","anioPublicacion":1965,"idioma":"Inglés","genero":"Ciencia Ficción",
                 "sinopsis":"Novela de ciencia ficción","portadaUrl":"http://example.com/dune.jpg",
                 "disponible":true},
                {"id":2,"titulo":"Fundación","autor":"Isaac Asimov","isbn":"978-0-553-29335-7",
                 "editorial":"Gnome Press","anioPublicacion":1951,"idioma":"Inglés","genero":"Ciencia Ficción",
                 "sinopsis":"Serie de ciencia ficción","portadaUrl":"http://example.com/fundacion.jpg",
                 "disponible":false}
              ],
              "pageable": {"pageNumber":0,"pageSize":20,"sort":{"sorted":true,"unsorted":false,"empty":false}},
              "totalPages":1,"totalElements":2,"size":20,"number":0,
              "sort":{"sorted":true,"unsorted":false,"empty":false},
              "first":true,"last":true,"empty":false
            }
            """;

        Page<LibroCatalogDTO> pagina = objectMapper.readValue(json, PAGE_TYPE);

        assertThat(pagina).isNotNull();
        assertThat(pagina.getTotalElements()).isEqualTo(2);
        assertThat(pagina.getNumber()).isZero();
        assertThat(pagina.getSize()).isEqualTo(20);
        assertThat(pagina.isFirst()).isTrue();
        assertThat(pagina.isLast()).isTrue();

        List<LibroCatalogDTO> contenido = pagina.getContent();
        assertThat(contenido).hasSize(2);
        assertThat(contenido.get(0).getTitulo()).isEqualTo("Dune");
        assertThat(contenido.get(0).getDisponible()).isTrue();
        assertThat(contenido.get(1).getTitulo()).isEqualTo("Fundación");
        assertThat(contenido.get(1).getDisponible()).isFalse();
    }

    @Test
    void deserializaPaginaConUnLibroYSegundaPagina() throws Exception {
        // totalElements = 21 es consistente con page=2, size=10, content=1
        // (2 * 10 + 1 = 21). El PageJacksonModule de OpenFeign computa
        // totalElements desde totalPages y content.size().
        String json = """
            {
              "content": [
                {"id":3,"titulo":"1984","autor":"George Orwell","isbn":"978-0-452-28423-4",
                 "editorial":"Secker & Warburg","anioPublicacion":1949,"idioma":"Inglés","genero":"Distopía",
                 "sinopsis":"Novela distópica","portadaUrl":"http://example.com/1984.jpg",
                 "disponible":true}
              ],
              "pageable": {"pageNumber":2,"pageSize":10,"sort":{"sorted":true,"unsorted":false,"empty":false}},
              "totalPages":3,"totalElements":21,"size":10,"number":2,
              "sort":{"sorted":true,"unsorted":false,"empty":false},
              "first":false,"last":false,"empty":false
            }
            """;

        Page<LibroCatalogDTO> pagina = objectMapper.readValue(json, PAGE_TYPE);

        assertThat(pagina.getTotalElements()).isEqualTo(21);
        assertThat(pagina.getTotalPages()).isEqualTo(3);
        assertThat(pagina.getNumber()).isEqualTo(2);
        assertThat(pagina.getSize()).isEqualTo(10);
        assertThat(pagina.getContent()).hasSize(1);
        assertThat(pagina.getContent().get(0).getTitulo()).isEqualTo("1984");
        assertThat(pagina.isFirst()).isFalse();
        // isLast() se computa de number + 1 >= totalPages (2 + 1 >= 3 = true)
        assertThat(pagina.isLast()).isTrue();
    }

    @Test
    void deserializaPaginaVacia() throws Exception {
        String json = """
            {
              "content": [],
              "pageable": {"pageNumber":0,"pageSize":20,"sort":{"sorted":false,"unsorted":true,"empty":true}},
              "totalPages":0,"totalElements":0,"size":20,"number":0,
              "sort":{"sorted":false,"unsorted":true,"empty":true},
              "first":true,"last":true,"empty":true
            }
            """;

        Page<LibroCatalogDTO> pagina = objectMapper.readValue(json, PAGE_TYPE);

        assertThat(pagina).isEmpty();
        assertThat(pagina.getTotalElements()).isZero();
        assertThat(pagina.getContent()).isEmpty();
    }

    @Test
    void deserializaPaginaConTodosLosCamposDelDto() throws Exception {
        String json = """
            {
              "content": [
                {"id":10,"titulo":"El nombre del viento","autor":"Patrick Rothfuss","isbn":"978-0-7564-0741-7",
                 "editorial":"DAW Books","anioPublicacion":2007,"idioma":"Inglés","genero":"Fantasía",
                 "sinopsis":"Una historia épica...","portadaUrl":"https://example.com/portada.jpg",
                 "disponible":true}
              ],
              "pageable": {"pageNumber":0,"pageSize":20,"sort":{"sorted":true,"unsorted":false,"empty":false}},
              "totalPages":1,"totalElements":1,"size":20,"number":0,
              "sort":{"sorted":true,"unsorted":false,"empty":false},
              "first":true,"last":true,"empty":false
            }
            """;

        Page<LibroCatalogDTO> pagina = objectMapper.readValue(json, PAGE_TYPE);

        assertThat(pagina.getContent()).hasSize(1);
        LibroCatalogDTO dto = pagina.getContent().get(0);
        assertThat(dto.getId()).isEqualTo(10L);
        assertThat(dto.getTitulo()).isEqualTo("El nombre del viento");
        assertThat(dto.getAutor()).isEqualTo("Patrick Rothfuss");
        assertThat(dto.getIsbn()).isEqualTo("978-0-7564-0741-7");
        assertThat(dto.getEditorial()).isEqualTo("DAW Books");
        assertThat(dto.getAnioPublicacion()).isEqualTo(2007);
        assertThat(dto.getIdioma()).isEqualTo("Inglés");
        assertThat(dto.getGenero()).isEqualTo("Fantasía");
        assertThat(dto.getSinopsis()).isEqualTo("Una historia épica...");
        assertThat(dto.getPortadaUrl()).isEqualTo("https://example.com/portada.jpg");
        assertThat(dto.getDisponible()).isTrue();
    }
}
