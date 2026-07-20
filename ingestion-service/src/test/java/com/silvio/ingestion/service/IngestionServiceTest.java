package com.silvio.ingestion.service;

import com.silvio.ingestion.dto.ArchivoLibroDTO;
import com.silvio.ingestion.exception.ErrorLecturaArchivoException;
import com.silvio.ingestion.exception.FormatoNoPermitidoException;
import com.silvio.ingestion.model.ArchivoLibro;
import com.silvio.ingestion.repository.ArchivoLibroInfo;
import com.silvio.ingestion.repository.ArchivoLibroRepository;
import com.silvio.ingestion.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

    // Bytes con magic bytes correctos para pruebas
    private static final byte[] PDF_MAGIC_BYTES = new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};
    private static final byte[] EPUB_MAGIC_BYTES = new byte[] {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00};
    private static final byte[] BYTES_INVALIDOS = "Contenido malicioso".getBytes();
    // Bytes con solo los 4 bytes mágicos exactos (límite inferior — tamaño mínimo válido)
    private static final byte[] PDF_MAGIC_EXACTO = new byte[] {0x25, 0x50, 0x44, 0x46};
    private static final byte[] EPUB_MAGIC_EXACTO = new byte[] {0x50, 0x4B, 0x03, 0x04};
    // 3 bytes — está por debajo del umbral de 4
    private static final byte[] TRES_BYTES = new byte[] {0x25, 0x50, 0x44};
    // Firma real de PNG (otro formato renombrado maliciosamente)
    private static final byte[] PNG_MAGIC_BYTES = new byte[] {(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

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

    // Proyección ligera para pruebas de obtenerInfo() — sin LONGBLOB
    private ArchivoLibroInfo archivoInfo(Long libroId) {
        LocalDateTime now = LocalDateTime.now();
        ArchivoLibroInfo info = mock(ArchivoLibroInfo.class);
        when(info.getId()).thenReturn(10L);
        when(info.getLibroId()).thenReturn(libroId);
        when(info.getNombreArchivo()).thenReturn("libro.pdf");
        when(info.getFormato()).thenReturn("PDF");
        when(info.getTamanio()).thenReturn(1024L);
        when(info.getFechaSubida()).thenReturn(now);
        return info;
    }

    // =====================================================================
    // subirArchivo() — subida exitosa de PDF
    // =====================================================================

    @Test
    void subirArchivo_formatoPDF_debeGuardarYRetornarDTO() {
        // MockMultipartFile simula un archivo PDF subido por formulario HTTP
        MockMultipartFile archivo = new MockMultipartFile(
            "archivo", "libro.pdf", "application/pdf", PDF_MAGIC_BYTES);

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
            "archivo", "libro.epub", "application/epub+zip", EPUB_MAGIC_BYTES);

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
            "archivo", "nuevo.pdf", "application/pdf", PDF_MAGIC_BYTES);

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
        ArchivoLibroInfo info = archivoInfo(1L);
        when(archivoRepository.findInfoByLibroId(1L)).thenReturn(Optional.of(info));

        ArchivoLibroDTO resultado = ingestionService.obtenerInfo(1L);

        assertThat(resultado.getLibroId()).isEqualTo(1L);
        assertThat(resultado.getFormato()).isEqualTo("PDF");
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

    // =====================================================================
    // obtenerInfo() — validación de proyección contra LONGBLOB
    // =====================================================================

    @Test
    void obtenerInfo_noDebeUsarFindByLibroId() {
        // Verifica que obtenerInfo() use la proyección (findInfoByLibroId)
        // y NO cargue la entidad completa (findByLibroId) que dispararía
        // el query al LONGBLOB
        ArchivoLibroInfo info = archivoInfo(1L);
        when(archivoRepository.findInfoByLibroId(1L)).thenReturn(Optional.of(info));

        ingestionService.obtenerInfo(1L);

        verify(archivoRepository).findInfoByLibroId(1L);
        verify(archivoRepository, never()).findByLibroId(anyLong());
    }

    // =====================================================================
    // subirArchivo() — IOException al leer bytes del MultipartFile
    // =====================================================================

    @Test
    void subirArchivo_ioException_alLeerBytes_lanzaError() throws Exception {
        // MockMultipartFile real no lanza IOException, pero el service llama
        // a archivo.getBytes() que sí puede fallar con un archivo corrupto.
        // Simulamos un archivo que lance IOException al llamar getBytes().
        MultipartFile archivoCorrupto = mock(MultipartFile.class);
        when(archivoCorrupto.getContentType()).thenReturn("application/pdf");
        when(archivoCorrupto.getOriginalFilename()).thenReturn("libro.pdf");
        when(archivoCorrupto.getSize()).thenReturn(1024L);
        when(archivoCorrupto.getBytes()).thenThrow(new IOException("Archivo corrupto"));

        when(archivoRepository.findByLibroId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ingestionService.subirArchivo(1L, archivoCorrupto))
            .isInstanceOf(ErrorLecturaArchivoException.class)
            .hasMessageContaining("Error al leer los bytes del archivo");

        // storageService.guardar() se invoca ANTES de archivo.getBytes()
        // en el flujo de subirArchivo(), por lo tanto sí se llama.
        // Lo que NO debe ocurrir es que se persista la entidad.
        verify(storageService).guardar(any(), anyLong());
        verify(archivoRepository, never()).save(any());
    }

    // =====================================================================
    // subirArchivo() + obtenerInfo() — reemplazo y verificación de nuevos metadatos
    // =====================================================================

    @Test
    void subirArchivo_reemplazoYInfoReflejaNuevosMetadatos() {
        // Arrange: existe un archivo anterior
        ArchivoLibro existente = archivoEntity(1L);
        when(archivoRepository.findByLibroId(1L)).thenReturn(Optional.of(existente));

        // Nuevo archivo con metadatos distintos
        MockMultipartFile nuevoArchivo = new MockMultipartFile(
            "archivo", "nueva_edicion.pdf", "application/pdf", PDF_MAGIC_BYTES);

        when(storageService.guardar(nuevoArchivo, 1L)).thenReturn("db:nuevo");
        when(archivoRepository.save(any())).thenAnswer(inv -> {
            ArchivoLibro a = inv.getArgument(0);
            a.setId(99L);
            return a;
        });

        // Act: reemplazar archivo
        ingestionService.subirArchivo(1L, nuevoArchivo);

        // Arrange para obtenerInfo: simular que la proyección devuelve los nuevos datos
        ArchivoLibroInfo infoNueva = mock(ArchivoLibroInfo.class);
        when(infoNueva.getId()).thenReturn(99L);
        when(infoNueva.getLibroId()).thenReturn(1L);
        when(infoNueva.getNombreArchivo()).thenReturn("nueva_edicion.pdf");
        when(infoNueva.getFormato()).thenReturn("PDF");
        when(infoNueva.getTamanio()).thenReturn(15L);
        when(archivoRepository.findInfoByLibroId(1L)).thenReturn(Optional.of(infoNueva));

        ArchivoLibroDTO resultado = ingestionService.obtenerInfo(1L);

        // Assert: los metadatos deben reflejar el archivo nuevo
        assertThat(resultado.getNombreArchivo()).isEqualTo("nueva_edicion.pdf");
        assertThat(resultado.getTamanio()).isEqualTo(15L);
        assertThat(resultado.getId()).isEqualTo(99L);
    }

    // =====================================================================
    // eliminar() — transaccionalidad: si storageService falla, no borrar de BD
    // =====================================================================

    @Test
    void eliminar_cuandoStorageFalla_noDebeBorrarDeBD() {
        // Arrange: existe un archivo
        ArchivoLibro archivo = archivoEntity(1L);
        when(archivoRepository.findByLibroId(1L)).thenReturn(Optional.of(archivo));
        // Storage falla al eliminar
        doThrow(new RuntimeException("Disco lleno")).when(storageService).eliminar("db:10");

        // Act & Assert
        assertThatThrownBy(() -> ingestionService.eliminar(1L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Disco lleno");

        // Verify: repository.delete NUNCA debe llamarse si storage falla
        // (la transacción de Spring debe hacer rollback automático)
        verify(archivoRepository, never()).delete(any());
    }

    // =====================================================================
    // subirArchivo() — validación de magic bytes
    // =====================================================================

    @Test
    void subirArchivo_pdfConMagicBytesCorrectos_debePasarValidacion() {
        // Given: archivo PDF con contenido real que comienza con %PDF
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "libro.pdf", "application/pdf", PDF_MAGIC_BYTES);

        when(archivoRepository.findByLibroId(1L)).thenReturn(Optional.empty());
        when(storageService.guardar(archivo, 1L)).thenReturn("db:1");
        when(archivoRepository.save(any())).thenAnswer(inv -> {
            ArchivoLibro a = inv.getArgument(0);
            a.setId(1L);
            return a;
        });

        // When
        ArchivoLibroDTO resultado = ingestionService.subirArchivo(1L, archivo);

        // Then: debe pasar validación y guardar
        assertThat(resultado.getFormato()).isEqualTo("PDF");
        verify(archivoRepository).save(any(ArchivoLibro.class));
    }

    @Test
    void subirArchivo_epubConMagicBytesCorrectos_debePasarValidacion() {
        // Given: archivo EPUB con contenido real que comienza con PK\x03\x04
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "libro.epub", "application/epub+zip", EPUB_MAGIC_BYTES);

        when(archivoRepository.findByLibroId(2L)).thenReturn(Optional.empty());
        when(storageService.guardar(archivo, 2L)).thenReturn("db:2");
        when(archivoRepository.save(any())).thenAnswer(inv -> {
            ArchivoLibro a = inv.getArgument(0);
            a.setId(2L);
            return a;
        });

        // When
        ArchivoLibroDTO resultado = ingestionService.subirArchivo(2L, archivo);

        // Then: debe pasar validación y guardar
        assertThat(resultado.getFormato()).isEqualTo("EPUB");
        verify(archivoRepository).save(any(ArchivoLibro.class));
    }

    @Test
    void subirArchivo_pdfExtensionPeroContenidoFalso_lanzaFormatoNoPermitido() {
        // Given: archivo con extensión .pdf pero contenido que no comienza con %PDF
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "malicioso.pdf", "application/pdf", BYTES_INVALIDOS);

        when(archivoRepository.findByLibroId(1L)).thenReturn(Optional.empty());
        when(storageService.guardar(archivo, 1L)).thenReturn("db:1");

        // When / Then: debe lanzar excepción por magic bytes inválidos
        assertThatThrownBy(() -> ingestionService.subirArchivo(1L, archivo))
                .isInstanceOf(FormatoNoPermitidoException.class)
                .hasMessageContaining("bytes mágicos");

        // No debe persistir en BD (storageService.guardar sí se llamó antes de validar bytes)
        verify(archivoRepository, never()).save(any());
        verify(storageService).guardar(archivo, 1L);
    }

    @Test
    void subirArchivo_epubExtensionPeroContenidoFalso_lanzaFormatoNoPermitido() {
        // Given: archivo con extensión .epub pero contenido que no comienza con ZIP
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "malicioso.epub", "application/epub+zip", BYTES_INVALIDOS);

        when(archivoRepository.findByLibroId(2L)).thenReturn(Optional.empty());
        when(storageService.guardar(archivo, 2L)).thenReturn("db:2");

        // When / Then: debe lanzar excepción por magic bytes inválidos
        assertThatThrownBy(() -> ingestionService.subirArchivo(2L, archivo))
                .isInstanceOf(FormatoNoPermitidoException.class)
                .hasMessageContaining("bytes mágicos");

        // No debe persistir en BD (storageService.guardar sí se llamó antes de validar bytes)
        verify(archivoRepository, never()).save(any());
        verify(storageService).guardar(archivo, 2L);
    }

    @Test
    void subirArchivo_archivoVacio_lanzaFormatoNoPermitido() {
        // Given: archivo vacío (sin contenido)
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "vacio.pdf", "application/pdf", new byte[0]);

        when(archivoRepository.findByLibroId(1L)).thenReturn(Optional.empty());
        when(storageService.guardar(archivo, 1L)).thenReturn("db:1");

        // When / Then: debe lanzar excepción por archivo vacío
        assertThatThrownBy(() -> ingestionService.subirArchivo(1L, archivo))
                .isInstanceOf(FormatoNoPermitidoException.class)
                .hasMessageContaining("vacío");

        // No debe persistir en BD (storageService.guardar sí se llamó antes de validar bytes)
        verify(archivoRepository, never()).save(any());
        verify(storageService).guardar(archivo, 1L);
    }

    @Test
    void subirArchivo_pdfMagicBytesExactos4Bytes_debePasarValidacion() {
        // Given: archivo PDF con exactamente 4 bytes (el mínimo necesario para validar)
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "minimo.pdf", "application/pdf", PDF_MAGIC_EXACTO);

        when(archivoRepository.findByLibroId(1L)).thenReturn(Optional.empty());
        when(storageService.guardar(archivo, 1L)).thenReturn("db:1");
        when(archivoRepository.save(any())).thenAnswer(inv -> {
            ArchivoLibro a = inv.getArgument(0);
            a.setId(1L);
            return a;
        });

        // When
        ArchivoLibroDTO resultado = ingestionService.subirArchivo(1L, archivo);

        // Then: debe pasar validación y guardar
        assertThat(resultado.getFormato()).isEqualTo("PDF");
        assertThat(resultado.getTamanio()).isEqualTo(4L);
        verify(archivoRepository).save(any(ArchivoLibro.class));
    }

    @Test
    void subirArchivo_epubMagicBytesExactos4Bytes_debePasarValidacion() {
        // Given: archivo EPUB con exactamente 4 bytes (el mínimo necesario para validar)
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "minimo.epub", "application/epub+zip", EPUB_MAGIC_EXACTO);

        when(archivoRepository.findByLibroId(2L)).thenReturn(Optional.empty());
        when(storageService.guardar(archivo, 2L)).thenReturn("db:2");
        when(archivoRepository.save(any())).thenAnswer(inv -> {
            ArchivoLibro a = inv.getArgument(0);
            a.setId(2L);
            return a;
        });

        // When
        ArchivoLibroDTO resultado = ingestionService.subirArchivo(2L, archivo);

        // Then: debe pasar validación y guardar
        assertThat(resultado.getFormato()).isEqualTo("EPUB");
        assertThat(resultado.getTamanio()).isEqualTo(4L);
        verify(archivoRepository).save(any(ArchivoLibro.class));
    }

    @Test
    void subirArchivo_archivoCon3Bytes_lanzaFormatoNoPermitido() {
        // Given: archivo con extensión .pdf pero solo 3 bytes (insuficientes para validar)
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "corto.pdf", "application/pdf", TRES_BYTES);

        when(archivoRepository.findByLibroId(1L)).thenReturn(Optional.empty());
        when(storageService.guardar(archivo, 1L)).thenReturn("db:1");

        // When / Then: debe lanzar excepción por archivo demasiado pequeño
        assertThatThrownBy(() -> ingestionService.subirArchivo(1L, archivo))
                .isInstanceOf(FormatoNoPermitidoException.class)
                .hasMessageContaining("demasiado pequeño");

        // No debe persistir en BD
        verify(archivoRepository, never()).save(any());
        verify(storageService).guardar(archivo, 1L);
    }

    @Test
    void subirArchivo_pngRenombradoAPdf_lanzaFormatoNoPermitido() {
        // Given: archivo con firma PNG real (0x89PNG...) pero extensión .pdf
        MockMultipartFile archivo = new MockMultipartFile(
                "archivo", "imagen.png.pdf", "application/pdf", PNG_MAGIC_BYTES);

        when(archivoRepository.findByLibroId(1L)).thenReturn(Optional.empty());
        when(storageService.guardar(archivo, 1L)).thenReturn("db:1");

        // When / Then: debe lanzar excepción porque los primeros 4 bytes no son %PDF
        assertThatThrownBy(() -> ingestionService.subirArchivo(1L, archivo))
                .isInstanceOf(FormatoNoPermitidoException.class)
                .hasMessageContaining("bytes mágicos");

        // No debe persistir en BD
        verify(archivoRepository, never()).save(any());
        verify(storageService).guardar(archivo, 1L);
    }
}
