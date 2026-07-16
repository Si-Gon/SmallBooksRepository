package com.silvio.content.controller;

import com.silvio.content.client.IngestionClient;
import com.silvio.content.client.LendingClient;
import com.silvio.content.dto.PrestamoDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de integración del ContentController.
 *
 * Verifican que content-service reciba X-User-Id del Gateway y lo propague
 * a elending-service a través del LendingClient Feign.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LendingClient lendingClient;

    @MockBean
    private IngestionClient ingestionClient;

    @Test
    void descargarArchivo_conXUserId_propagaUsuarioIdALendingClient() throws Exception {
        String usuarioId = "usuario_content";
        byte[] contenido = "contenido del pdf".getBytes();

        PrestamoDTO prestamo = new PrestamoDTO();
        prestamo.setId(1L);
        prestamo.setUsuarioId(usuarioId);
        prestamo.setLibroId(1L);
        prestamo.setEstado("ACTIVO");

        when(lendingClient.obtenerPrestamosActivos(usuarioId))
                .thenReturn(List.of(prestamo));
        when(ingestionClient.obtenerBytes(1L))
                .thenReturn(contenido);

        mockMvc.perform(get("/api/content/1")
                        .header("X-User-Id", usuarioId))
                .andExpect(status().isOk())
                .andExpect(content().bytes(contenido));

        verify(lendingClient).obtenerPrestamosActivos(eq(usuarioId));
    }

    @Test
    void descargarArchivo_conAuthorizationSinXUserId_debeRetornar400() throws Exception {
        mockMvc.perform(get("/api/content/1")
                        .header("Authorization", "Bearer token.fake"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void descargarArchivo_sinXUserId_debeRetornar400() throws Exception {
        mockMvc.perform(get("/api/content/1"))
                .andExpect(status().isBadRequest());
    }
}
