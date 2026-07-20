package com.silvio.ingestion.service;

import com.silvio.ingestion.dto.ArchivoLibroDTO;
import com.silvio.ingestion.model.ArchivoLibro;
import com.silvio.ingestion.repository.ArchivoLibroInfo;
import com.silvio.ingestion.repository.ArchivoLibroRepository;
import com.silvio.ingestion.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.silvio.ingestion.exception.ErrorLecturaArchivoException;
import com.silvio.ingestion.exception.FormatoNoPermitidoException;
import io.micrometer.observation.annotation.Observed;

import java.io.IOException;
import java.util.NoSuchElementException;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final ArchivoLibroRepository archivoRepository;
    private final StorageService storageService;

    private static final List<String> FORMATOS_PERMITIDOS = List.of(
            "application/pdf",
            "application/epub+zip"
    );

    // Magic bytes para validación de contenido real del archivo
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46}; // %PDF
    private static final byte[] EPUB_MAGIC = {0x50, 0x4B, 0x03, 0x04}; // ZIP signature (EPUB)

    @Observed(name = "ingestion.subirArchivo")
    @Transactional
    public ArchivoLibroDTO subirArchivo(Long libroId, MultipartFile archivo) {
        log.info("Subiendo archivo para libro id: {}, nombre: {}, tamaño: {} bytes",
                libroId, archivo.getOriginalFilename(), archivo.getSize());

        String contentType = archivo.getContentType();
        if (!FORMATOS_PERMITIDOS.contains(contentType)) {
            log.warn("Formato no permitido: {} para libro id: {}", contentType, libroId);
            throw new FormatoNoPermitidoException(contentType);
        }

        archivoRepository.findByLibroId(libroId).ifPresent(existente -> {
            log.info("Reemplazando archivo existente para libro id: {}", libroId);
            storageService.eliminar(existente.getRutaOClave());
            archivoRepository.delete(existente);
        });

        String rutaOClave = storageService.guardar(archivo, libroId); // devuelve "db:1"
        String formato = contentType.contains("pdf") ? "PDF" : "EPUB";

        ArchivoLibro archivoLibro = new ArchivoLibro();
        archivoLibro.setLibroId(libroId);
        archivoLibro.setNombreArchivo(archivo.getOriginalFilename());
        archivoLibro.setFormato(formato);
        archivoLibro.setTamanio(archivo.getSize());
        archivoLibro.setRutaOClave(rutaOClave);
        archivoLibro.setFechaSubida(LocalDateTime.now());
        try {
            byte[] bytes = archivo.getBytes();
            validarMagicBytes(bytes, formato);
            archivoLibro.setDatos(bytes);
        } catch (IOException e) {
            throw new ErrorLecturaArchivoException(e.getMessage());
        }

        ArchivoLibro guardado = archivoRepository.save(archivoLibro);
        log.info("Archivo subido exitosamente — id: {}, libro: {}, formato: {}, ruta: {}",
                guardado.getId(), libroId, formato, rutaOClave);
        return mapearADto(guardado);
    }

    @Observed(name = "ingestion.obtenerInfo")
    @Transactional(readOnly = true)
    public ArchivoLibroDTO obtenerInfo(Long libroId) {
        log.info("Consultando info de archivo para libro id: {}", libroId);
        ArchivoLibroInfo info = archivoRepository.findInfoByLibroId(libroId)
                .orElseThrow(() -> {
                    log.warn("No hay archivo para libro id: {}", libroId);
                    return new NoSuchElementException("No hay archivo subido para el libro con id: " + libroId);
                });
        return mapearADto(info);
    }

    @Observed(name = "ingestion.obtenerBytes")
    @Transactional(readOnly = true)
    public byte[] obtenerBytes(Long libroId) {
        log.info("Obteniendo bytes del archivo para libro id: {}", libroId);
        ArchivoLibro archivo = archivoRepository.findByLibroId(libroId)
                .orElseThrow(() -> new NoSuchElementException("No hay archivo subido para el libro con id: " + libroId));
        return storageService.obtener(archivo.getRutaOClave());
    }

    @Observed(name = "ingestion.eliminar")
    @Transactional
    public void eliminar(Long libroId) {
        log.info("Eliminando archivo para libro id: {}", libroId);
        ArchivoLibro archivo = archivoRepository.findByLibroId(libroId)
                .orElseThrow(() -> new NoSuchElementException("No hay archivo subido para el libro con id: " + libroId));

        storageService.eliminar(archivo.getRutaOClave());
        archivoRepository.delete(archivo);
        log.info("Archivo eliminado exitosamente para libro id: {}", libroId);
    }

    // Valida que los primeros bytes del archivo coincidan con el formato declarado
    private void validarMagicBytes(byte[] bytes, String formato) {
        if (bytes == null || bytes.length < 4) {
            throw new FormatoNoPermitidoException(
                    "archivo vacío o demasiado pequeño — no tiene formato " + formato);
        }
        byte[] expected = "PDF".equals(formato) ? PDF_MAGIC : EPUB_MAGIC;
        for (int i = 0; i < 4; i++) {
            if (bytes[i] != expected[i]) {
                throw new FormatoNoPermitidoException(
                        "Los bytes mágicos no corresponden a " + formato);
            }
        }
    }

    private ArchivoLibroDTO mapearADto(ArchivoLibro archivo) {
        ArchivoLibroDTO dto = new ArchivoLibroDTO();
        dto.setId(archivo.getId());
        dto.setLibroId(archivo.getLibroId());
        dto.setNombreArchivo(archivo.getNombreArchivo());
        dto.setFormato(archivo.getFormato());
        dto.setTamanio(archivo.getTamanio());
        dto.setFechaSubida(archivo.getFechaSubida());
        return dto;
    }

    private ArchivoLibroDTO mapearADto(ArchivoLibroInfo info) {
        ArchivoLibroDTO dto = new ArchivoLibroDTO();
        dto.setId(info.getId());
        dto.setLibroId(info.getLibroId());
        dto.setNombreArchivo(info.getNombreArchivo());
        dto.setFormato(info.getFormato());
        dto.setTamanio(info.getTamanio());
        dto.setFechaSubida(info.getFechaSubida());
        return dto;
    }
}