package com.silvio.content.service;

import com.silvio.content.client.IngestionClient;
import com.silvio.content.client.LendingClient;
import com.silvio.content.dto.PrestamoDTO;
import com.silvio.content.exception.AccesoDenegadoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de ContentService.
 *
 * El flujo del servicio tiene 3 pasos:
 * 1. Pedir préstamos activos al LendingClient (FeignClient)
 * 2. Verificar que el libro pedido tiene estado "ACTIVO" en esa lista
 * 3. Pedir los bytes del archivo al IngestionClient (FeignClient)
 *
 * Cada paso puede fallar de forma distinta, y eso se refleja en los tests.
 * Usamos @ExtendWith(MockitoExtension) — sin Spring, solo lógica pura.
 */
@ExtendWith(MockitoExtension.class)
class ContentServiceTest {

    @Mock
    private LendingClient lendingClient;

    @Mock
    private IngestionClient ingestionClient;

    @InjectMocks
    private ContentService contentService;

    private PrestamoDTO prestamo(Long libroId, String estado) {
        PrestamoDTO p = new PrestamoDTO();
        p.setId(1L);
        p.setUsuarioId("silvio");
        p.setLibroId(libroId);
        p.setEstado(estado);
        return p;
    }

    // =====================================================================
    // Caso exitoso: préstamo activo + archivo disponible
    // =====================================================================

    @Test
    void obtenerArchivo_conPrestamoActivo_retornaBytes() {
        byte[] bytes = "contenido del pdf".getBytes();
        when(lendingClient.obtenerPrestamosActivos("silvio"))
            .thenReturn(List.of(prestamo(1L, "ACTIVO")));
        when(ingestionClient.obtenerBytes(1L)).thenReturn(bytes);

        byte[] resultado = contentService.obtenerArchivo(1L, "silvio");

        assertThat(resultado).isEqualTo(bytes);
        verify(lendingClient).obtenerPrestamosActivos("silvio");
        verify(ingestionClient).obtenerBytes(1L);
    }

    // =====================================================================
    // Caso: préstamo existe pero con estado VENCIDO — acceso denegado
    // =====================================================================

    @Test
    void obtenerArchivo_prestamoVencido_lanzaAccesoDenegado() {
        // El usuario tiene el libro pero VENCIDO — no puede descargar
        when(lendingClient.obtenerPrestamosActivos("silvio"))
            .thenReturn(List.of(prestamo(1L, "VENCIDO")));

        assertThatThrownBy(() -> contentService.obtenerArchivo(1L, "silvio"))
            .isInstanceOf(AccesoDenegadoException.class)
            .hasMessageContaining("Acceso denegado");

        // No debe ni intentar obtener el archivo si no tiene préstamo activo
        verify(ingestionClient, never()).obtenerBytes(any());
    }

    // =====================================================================
    // Caso: el libro no está en la lista de préstamos
    // =====================================================================

    @Test
    void obtenerArchivo_libroDiferente_lanzaAccesoDenegado() {
        // El usuario tiene prestado el libro 99, pero pide el libro 1
        when(lendingClient.obtenerPrestamosActivos("silvio"))
            .thenReturn(List.of(prestamo(99L, "ACTIVO")));

        assertThatThrownBy(() -> contentService.obtenerArchivo(1L, "silvio"))
            .isInstanceOf(AccesoDenegadoException.class)
            .hasMessageContaining("Acceso denegado");

        verify(ingestionClient, never()).obtenerBytes(any());
    }

    // =====================================================================
    // Caso: sin préstamos activos (lista vacía)
    // =====================================================================

    @Test
    void obtenerArchivo_sinPrestamos_lanzaAccesoDenegado() {
        when(lendingClient.obtenerPrestamosActivos("silvio"))
            .thenReturn(List.of());

        assertThatThrownBy(() -> contentService.obtenerArchivo(1L, "silvio"))
            .isInstanceOf(AccesoDenegadoException.class)
            .hasMessageContaining("Acceso denegado");

        verify(ingestionClient, never()).obtenerBytes(any());
    }

    // =====================================================================
    // Caso: LendingClient falla (elending-service caído)
    // =====================================================================

    @Test
    void obtenerArchivo_errorEnLending_lanzaNoSePudoVerificar() {
        when(lendingClient.obtenerPrestamosActivos("silvio"))
            .thenThrow(new RuntimeException("Connection refused"));

        // El servicio ya no envuelve excepciones — se propagan directamente
        assertThatThrownBy(() -> contentService.obtenerArchivo(1L, "silvio"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Connection refused");

        verify(ingestionClient, never()).obtenerBytes(any());
    }

    // =====================================================================
    // Caso: IngestionClient falla (archivo no encontrado / servicio caído)
    // =====================================================================

    @Test
    void obtenerArchivo_errorEnIngestion_lanzaNoSePudoObtener() {
        when(lendingClient.obtenerPrestamosActivos("silvio"))
            .thenReturn(List.of(prestamo(1L, "ACTIVO")));
        when(ingestionClient.obtenerBytes(1L))
            .thenThrow(new RuntimeException("404 Not Found"));

        // El servicio ya no envuelve excepciones — se propagan directamente
        assertThatThrownBy(() -> contentService.obtenerArchivo(1L, "silvio"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("404 Not Found");
    }
}
