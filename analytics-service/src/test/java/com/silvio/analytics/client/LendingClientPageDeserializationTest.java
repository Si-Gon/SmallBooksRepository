package com.silvio.analytics.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.silvio.analytics.dto.PrestamoAnalyticsDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests de deserialización JSON de Page<PrestamoAnalyticsDTO>.
 *
 * Verifica que Jackson (usado por OpenFeign) pueda deserializar
 * la respuesta paginada de elending-service en distintos escenarios.
 *
 * spring-data-commons provee el PageJacksonModule necesario para
 * que Jackson entienda el formato Page de Spring Data.
 */
@SpringBootTest
@ActiveProfiles("test")
class LendingClientPageDeserializationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private static final TypeReference<Page<PrestamoAnalyticsDTO>> PAGE_TYPE =
            new TypeReference<Page<PrestamoAnalyticsDTO>>() {};

    @Test
    void deserializaPaginaConDosPrestamos() throws Exception {
        String json = """
            {
              "content": [
                {"id":1,"usuarioId":"silvio","libroId":100,"estado":"ACTIVO",
                 "fechaInicio":"2026-07-01T10:00:00","fechaVencimiento":"2026-07-15T10:00:00"},
                {"id":2,"usuarioId":"ana","libroId":200,"estado":"VENCIDO",
                 "fechaInicio":"2026-06-01T10:00:00","fechaVencimiento":"2026-06-15T10:00:00"}
              ],
              "pageable": {"pageNumber":0,"pageSize":50,"sort":{"sorted":true,"unsorted":false,"empty":false}},
              "totalPages":1,"totalElements":2,"size":50,"number":0,
              "sort":{"sorted":true,"unsorted":false,"empty":false},
              "first":true,"last":true,"empty":false
            }
            """;

        Page<PrestamoAnalyticsDTO> pagina = objectMapper.readValue(json, PAGE_TYPE);

        assertThat(pagina).isNotNull();
        assertThat(pagina.getTotalElements()).isEqualTo(2);
        assertThat(pagina.getNumber()).isZero();
        assertThat(pagina.getSize()).isEqualTo(50);
        assertThat(pagina.getTotalPages()).isEqualTo(1);
        assertThat(pagina.isFirst()).isTrue();
        assertThat(pagina.isLast()).isTrue();
        assertThat(pagina.isEmpty()).isFalse();

        List<PrestamoAnalyticsDTO> contenido = pagina.getContent();
        assertThat(contenido).hasSize(2);
        assertThat(contenido.get(0).getUsuarioId()).isEqualTo("silvio");
        assertThat(contenido.get(0).getLibroId()).isEqualTo(100L);
        assertThat(contenido.get(0).getEstado()).isEqualTo("ACTIVO");
        assertThat(contenido.get(1).getUsuarioId()).isEqualTo("ana");
        assertThat(contenido.get(1).getLibroId()).isEqualTo(200L);
        assertThat(contenido.get(1).getEstado()).isEqualTo("VENCIDO");
    }

    @Test
    void deserializaPaginaConUnaSolaPagina() throws Exception {
        // totalElements = 1, totalPages = 1, content = 1
        String json = """
            {
              "content": [
                {"id":5,"usuarioId":"pedro","libroId":300,"estado":"ACTIVO",
                 "fechaInicio":"2026-07-10T10:00:00","fechaVencimiento":"2026-07-24T10:00:00"}
              ],
              "pageable": {"pageNumber":0,"pageSize":50,"sort":{"sorted":true,"unsorted":false,"empty":false}},
              "totalPages":1,"totalElements":1,"size":50,"number":0,
              "sort":{"sorted":true,"unsorted":false,"empty":false},
              "first":true,"last":true,"empty":false
            }
            """;

        Page<PrestamoAnalyticsDTO> pagina = objectMapper.readValue(json, PAGE_TYPE);

        assertThat(pagina.getTotalElements()).isEqualTo(1);
        assertThat(pagina.getTotalPages()).isEqualTo(1);
        assertThat(pagina.getContent()).hasSize(1);
        assertThat(pagina.getContent().get(0).getUsuarioId()).isEqualTo("pedro");
        assertThat(pagina.getContent().get(0).getEstado()).isEqualTo("ACTIVO");
    }

    @Test
    void deserializaPaginaVacia() throws Exception {
        String json = """
            {
              "content": [],
              "pageable": {"pageNumber":0,"pageSize":50,"sort":{"sorted":false,"unsorted":true,"empty":true}},
              "totalPages":0,"totalElements":0,"size":50,"number":0,
              "sort":{"sorted":false,"unsorted":true,"empty":true},
              "first":true,"last":true,"empty":true
            }
            """;

        Page<PrestamoAnalyticsDTO> pagina = objectMapper.readValue(json, PAGE_TYPE);

        assertThat(pagina).isEmpty();
        assertThat(pagina.getTotalElements()).isZero();
        assertThat(pagina.getContent()).isEmpty();
        assertThat(pagina.getNumber()).isZero();
    }

    @Test
    void deserializaPaginaConMultiplesPaginas() throws Exception {
        // totalElements = 51 (consistente con page=1, size=50, content=1)
        // El PageJacksonModule de OpenFeign computa totalElements desde
        // totalPages y content.size(): (2-1)*50 + 1 = 51
        String json = """
            {
              "content": [
                {"id":51,"usuarioId":"lucia","libroId":500,"estado":"ACTIVO",
                 "fechaInicio":"2026-07-15T10:00:00","fechaVencimiento":"2026-07-29T10:00:00"}
              ],
              "pageable": {"pageNumber":1,"pageSize":50,"sort":{"sorted":true,"unsorted":false,"empty":false}},
              "totalPages":2,"totalElements":51,"size":50,"number":1,
              "sort":{"sorted":true,"unsorted":false,"empty":false},
              "first":false,"last":true,"empty":false
            }
            """;

        Page<PrestamoAnalyticsDTO> pagina = objectMapper.readValue(json, PAGE_TYPE);

        assertThat(pagina.getTotalElements()).isEqualTo(51);
        assertThat(pagina.getTotalPages()).isEqualTo(2);
        assertThat(pagina.getNumber()).isEqualTo(1);
        assertThat(pagina.getSize()).isEqualTo(50);
        assertThat(pagina.getContent()).hasSize(1);
        assertThat(pagina.getContent().get(0).getUsuarioId()).isEqualTo("lucia");
        assertThat(pagina.isFirst()).isFalse();
        // isLast() se computa de number + 1 >= totalPages (1 + 1 >= 2 = true)
    }

    @Test
    void deserializaPagina_conCamposIgnorados_porJsonIgnoreProperties() throws Exception {
        // PrestamoAnalyticsDTO tiene @JsonIgnoreProperties(ignoreUnknown = true)
        // Verifica que campos extras en el JSON no rompen la deserializacion
        String json = """
            {
              "content": [
                {"id":99,"usuarioId":"test","libroId":999,"estado":"ACTIVO",
                 "fechaInicio":"2026-07-01T10:00:00","fechaVencimiento":"2026-07-15T10:00:00",
                 "campoExtra":"ignorado","otroCampo":42}
              ],
              "pageable": {"pageNumber":0,"pageSize":50,"sort":{"sorted":false,"unsorted":true,"empty":true}},
              "totalPages":1,"totalElements":1,"size":50,"number":0,
              "sort":{"sorted":false,"unsorted":true,"empty":true},
              "first":true,"last":true,"empty":false,
              "metadatoExtra":"tambien ignorado"
            }
            """;

        Page<PrestamoAnalyticsDTO> pagina = objectMapper.readValue(json, PAGE_TYPE);

        assertThat(pagina.getContent()).hasSize(1);
        assertThat(pagina.getContent().get(0).getId()).isEqualTo(99L);
        assertThat(pagina.getContent().get(0).getUsuarioId()).isEqualTo("test");
        assertThat(pagina.getTotalElements()).isEqualTo(1);
    }
}
