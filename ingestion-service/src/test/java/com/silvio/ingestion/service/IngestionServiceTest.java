package com.silvio.ingestion.service;

import com.silvio.ingestion.dto.ArchivoLibroDTO;
import com.silvio.ingestion.exception.ArchivoNoEncontradoException;
import com.silvio.ingestion.exception.FormatoNoPermitidoException;
import com.silvio.ingestion.model.ArchivoLibro;
import com.silvio.ingestion.repository.ArchivoLibroRepository;
import com.silvio.ingestion.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de IngestionService.
 *
 * Puntos clave del servicio a testear:
 * 1. Validación de formato (solo PDF y EPUB)
 * 2. Reemplazo de archivo si ya existe uno para el libroId
 * 3. Guardado exitoso y mapeo a DTO
 * 4. obtenerInfo() lanza excepción si no existe
 * 5. obtenerBytes() delega en storageService
 * 6. eliminar() lanza excepción si no existe
 *
 * MockMultipartFile: clase de Spring Test que simula un archivo subido por HTTP
 * sin necesidad de un servidor real ni de un archivo en disco.
 */
@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock
    private ArchivoLibroRepository archivoRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private IngestionService ingestionService;

    private ArchivoLibro archivoEntity(Long libroId) {
        ArchivoLibro a = new ArchivoLibro();
        a.setId(10L);
        a.setLibroId(libroId);
        a.setNombreArchivo("libro.pdf");
        a.setFormato("PDF");
        a.setTamanio(1024L);
        a.setRutaOClave("db:10");
        a.setFechaSubida(LocalDateTime.now());
        a.setDatos("contenido".getBytes());
        return a;
    }

    // =====================================================================
    // subirArchivo() — subida exitosa de PDF
    // =====================================================================

    @Test
    void subirArchivo_formatoPDF_debeGuardarYRetornarDTO() {
        // MockMultipartFile simula un archivo PDF subido por formulario HTTP
        MockMultipartFile archivo = new MockMultipartFile(
            "archivo", "libro.pdf", "application/pdf", "contenido pdf".getBytes());

        when(archivoRepository.findByLibroId(1L)).thenReturn(Optional.empty());
        when(storageService.guardar(archivo, 1L)).thenReturn("db:1");
        when(archivoRepository.save(any())).thenAnswer(inv -> {
            ArchivoLibro a = inv.getArgument(0);
            a.setId(1L);
            return a;
        });

        ArchivoLibroDTO resultado = ingestionService.subirArchivo(1L, archivo);

        assertThat(resultado.getLibroId()).isEqualTo(1L);
        assertThat(resultado.getFormato()).isEqualTo("PDF");
        assertThat(resultado.getNombreArchivo()).isEqualTo("libro.pdf");

        verify(archivoRepository).save(any(ArchivoLibro.class));
        verify(storageService).guardar(archivo, 1L);
    }

    @Test
    void subirArchivo_formatoEPUB_debeGuardarYRetornarDTO() {
        MockMultipartFile archivo = new MockMultipartFile(
            "archivo", "libro.epub", "application/epub+zip", "contenido epub".getBytes());

        when(archivoRepository.findByLibroId(2L)).thenReturn(Optional.empty());
        when(storageService.guardar(archivo, 2L)).thenReturn("db:2");
        when(archivoRepository.save(any())).thenAnswer(inv -> {
            ArchivoLibro a = inv.getArgument(0);
            a.setId(2L);
            return a;
        });

        ArchivoLibroDTO resultado = ingestionService.subirArchivo(2L, archivo);

        assertThat(resultado.getFormato()).isEqualTo("EPUB");
    }

    // =====================================================================
    // subirArchivo() — formato no permitido
    // =====================================================================

    @Test
    void subirArchivo_formatoNoPermitido_debeLanzarExcepcion() {
        MockMultipartFile archivo = new MockMultipartFile(
            "archivo", "documento.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "contenido".getBytes());

        // No debe llegar a guardar ni a validar si existe — falla antes
        assertThatThrownBy(() -> ingestionService.subirArchivo(1L, archivo))
            .isInstanceOf(FormatoNoPermitidoException.class)
            .hasMessageContaining("Formato no permitido");

        verify(archivoRepository, never()).save(any());
        verify(storageService, never()).guardar(any(), any());
    }

    // =====================================================================
    // subirArchivo() — reemplazo cuando ya existe un archivo
    // =====================================================================

    @Test
    void subirArchivo_archivoYaExiste_debeReemplazarElAnterior() {
        MockMultipartFile archivo = new MockMultipartFile(
            "archivo", "nuevo.pdf", "application/pdf", "nuevo contenido".getBytes());

        ArchivoLibro existente = archivoEntity(1L);
        when(archivoRepository.findByLibroId(1L)).thenReturn(Optional.of(existente));
        when(storageService.guardar(archivo, 1L)).thenReturn("db:nuevo");
        when(archivoRepository.save(any())).thenAnswer(inv -> {
            ArchivoLibro a = inv.getArgument(0);
            a.setId(99L);
            return a;
        });

        ingestionService.subirArchivo(1L, archivo);

        // Debe eliminar el anterior antes de guardar el nuevo
        verify(storageService).eliminar("db:10");
        verify(archivoRepository).delete(existente);
        verify(archivoRepository).save(any(ArchivoLibro.class));
    }

    // =====================================================================
    // obtenerInfo()
    // =====================================================================

    @Test
    void obtenerInfo_archivoExiste_retornaDTO() {
        when(archivoRepository.findByLibroId(1L)).thenReturn(Optional.of(archivoEntity(1L)));

        ArchivoLibroDTO resultado = ingestionService.obtenerInfo(1L);

        assertThat(resultado.getLibroId()).isEqualTo(1L);
        assertThat(resultado.getFormato()).isEqualTo("PDF");
    }

    @Test
    void obtenerInfo_archivoNoExiste_lanzaExcepcion() {
        when(archivoRepository.findByLibroId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ingestionService.obtenerInfo(99L))
            .isInstanceOf(ArchivoNoEncontradoException.class)
            .hasMessageContaining("No hay archivo subido para el libro con id: 99");
    }

    // =====================================================================
    // obtenerBytes()
    // =====================================================================

    @Test
    void obtenerBytes_archivoExiste_retornaBytes() {
        byte[] bytes = "datos del pdf".getBytes();
        ArchivoLibro archivo = archivoEntity(1L);
        when(archivoRepository.findByLibroId(1L)).thenReturn(Optional.of(archivo));
        when(storageService.obtener("db:10")).thenReturn(bytes);

        byte[] resultado = ingestionService.obtenerBytes(1L);

        assertThat(resultado).isEqualTo(bytes);
        verify(storageService).obtener("db:10");
    }

    @Test
    void obtenerBytes_archivoNoExiste_lanzaExcepcion() {
        when(archivoRepository.findByLibroId(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ingestionService.obtenerBytes(5L))
            .isInstanceOf(ArchivoNoEncontradoException.class)
            .hasMessageContaining("No hay archivo subido para el libro con id: 5");
    }

    // =====================================================================
    // eliminar()
    // =====================================================================

    @Test
    void eliminar_archivoExiste_debeEliminar() {
        ArchivoLibro archivo = archivoEntity(1L);
        when(archivoRepository.findByLibroId(1L)).thenReturn(Optional.of(archivo));

        ingestionService.eliminar(1L);

        verify(storageService).eliminar("db:10");
        verify(archivoRepository).delete(archivo);
    }

    @Test
    void eliminar_archivoNoExiste_lanzaExcepcion() {
        when(archivoRepository.findByLibroId(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ingestionService.eliminar(7L))
            .isInstanceOf(ArchivoNoEncontradoException.class)
            .hasMessageContaining("No hay archivo subido para el libro con id: 7");

        verify(storageService, never()).eliminar(any());
        verify(archivoRepository, never()).delete(any());
    }
}
