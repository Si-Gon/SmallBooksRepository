package com.silvio.ingestion.service;

import com.silvio.ingestion.dto.ArchivoLibroDTO;
import com.silvio.ingestion.model.ArchivoLibro;
import com.silvio.ingestion.repository.ArchivoLibroRepository;
import com.silvio.ingestion.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.silvio.ingestion.exception.ArchivoNoEncontradoException;
import com.silvio.ingestion.exception.ErrorLecturaArchivoException;
import com.silvio.ingestion.exception.FormatoNoPermitidoException;
import io.micrometer.observation.annotation.Observed;

import java.io.IOException;

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
            archivoLibro.setDatos(archivo.getBytes());
            } catch (IOException e) {
            throw new ErrorLecturaArchivoException(e.getMessage());
        }

        ArchivoLibro guardado = archivoRepository.save(archivoLibro);
        log.info("Archivo subido exitosamente — id: {}, libro: {}, formato: {}, ruta: {}",
                guardado.getId(), libroId, formato, rutaOClave);
        return mapearADto(guardado);
    }

    @Observed(name = "ingestion.obtenerInfo")
    public ArchivoLibroDTO obtenerInfo(Long libroId) {
        log.info("Consultando info de archivo para libro id: {}", libroId);
        ArchivoLibro archivo = archivoRepository.findByLibroId(libroId)
                .orElseThrow(() -> {
                    log.warn("No hay archivo para libro id: {}", libroId);
                    return new ArchivoNoEncontradoException(libroId);
                });
        return mapearADto(archivo);
    }

    @Observed(name = "ingestion.obtenerBytes")
    public byte[] obtenerBytes(Long libroId) {
        log.info("Obteniendo bytes del archivo para libro id: {}", libroId);
        ArchivoLibro archivo = archivoRepository.findByLibroId(libroId)
                .orElseThrow(() -> new ArchivoNoEncontradoException(libroId));
        return storageService.obtener(archivo.getRutaOClave());
    }

    @Observed(name = "ingestion.eliminar")
    @Transactional
    public void eliminar(Long libroId) {
        log.info("Eliminando archivo para libro id: {}", libroId);
        ArchivoLibro archivo = archivoRepository.findByLibroId(libroId)
                .orElseThrow(() -> new ArchivoNoEncontradoException(libroId));

        storageService.eliminar(archivo.getRutaOClave());
        archivoRepository.delete(archivo);
        log.info("Archivo eliminado exitosamente para libro id: {}", libroId);
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
}