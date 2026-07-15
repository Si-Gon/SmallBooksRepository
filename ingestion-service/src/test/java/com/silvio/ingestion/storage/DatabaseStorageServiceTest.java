package com.silvio.ingestion.storage;

import com.silvio.ingestion.exception.ErrorAlmacenamientoException;
import com.silvio.ingestion.model.ArchivoLibro;
import com.silvio.ingestion.repository.ArchivoLibroRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests de DatabaseStorageService.
 *
 * DatabaseStorageService es la implementacion alternativa de StorageService
 * que lee los bytes del LONGBLOB usando el repositorio directamente.
 */
@ExtendWith(MockitoExtension.class)
class DatabaseStorageServiceTest {

    @Mock
    private ArchivoLibroRepository archivoRepository;

    @InjectMocks
    private DatabaseStorageService databaseStorageService;

    private ArchivoLibro archivoConDatos(Long libroId, byte[] datos) {
        ArchivoLibro a = new ArchivoLibro();
        a.setId(libroId);
        a.setLibroId(libroId);
        a.setNombreArchivo("libro.pdf");
        a.setFormato("PDF");
        a.setTamanio((long) datos.length);
        a.setRutaOClave("db:" + libroId);
        a.setFechaSubida(LocalDateTime.now());
        a.setDatos(datos);
        return a;
    }

    @Test
    void obtener_conRutaDb_debeRetornarBytes() {
        byte[] datosEsperados = "contenido del pdf almacenado en blob".getBytes();
        ArchivoLibro archivo = archivoConDatos(1L, datosEsperados);
        when(archivoRepository.findByLibroId(1L)).thenReturn(Optional.of(archivo));

        byte[] resultado = databaseStorageService.obtener("db:1");

        assertThat(resultado).isEqualTo(datosEsperados);
        verify(archivoRepository).findByLibroId(1L);
    }

    @Test
    void obtener_rutaInvalida_lanzaErrorAlmacenamiento() {
        when(archivoRepository.findByLibroId(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> databaseStorageService.obtener("db:999"))
            .isInstanceOf(ErrorAlmacenamientoException.class)
            .hasMessageContaining("Archivo no encontrado");
    }

    @Test
    void guardar_debeDevolverClaveDb() {
        String ruta = databaseStorageService.guardar(null, 5L);
        assertThat(ruta).isEqualTo("db:5");
    }

    @Test
    void eliminar_noDebeLlamarAlRepositorio() {
        databaseStorageService.eliminar("db:1");
        // DatabaseStorageService.eliminar() es no-op
        verifyNoInteractions(archivoRepository);
    }
}
