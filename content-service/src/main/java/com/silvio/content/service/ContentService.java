package com.silvio.content.service;

import com.silvio.content.client.IngestionClient;
import com.silvio.content.client.LendingClient;
import com.silvio.content.dto.PrestamoDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.silvio.content.exception.AccesoDenegadoException;
import io.micrometer.observation.annotation.Observed;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentService {

    private final LendingClient lendingClient;
    private final IngestionClient ingestionClient;

    @Observed(name = "content.obtenerArchivo")
    public byte[] obtenerArchivo(Long libroId, String usuarioId) {
        log.info("Solicitud de acceso al archivo del libro id: {}", libroId);

        // Paso 1: Verificar préstamo activo
        List<PrestamoDTO> prestamosActivos = lendingClient.obtenerPrestamosActivos(usuarioId);
        log.info("Préstamos activos encontrados: {}", prestamosActivos.size());

        // Paso 2: Verificar que tiene el libro
        boolean tienePrestamo = prestamosActivos.stream()
                .anyMatch(p -> p.getLibroId().equals(libroId) &&
                               "ACTIVO".equals(p.getEstado()));

        if (!tienePrestamo) {
            log.warn("Acceso denegado — sin préstamo activo para libro id: {}", libroId);
            throw new AccesoDenegadoException(libroId);
        }

        log.info("Préstamo verificado — entregando archivo del libro id: {}", libroId);

        // Paso 3: Obtener el archivo
        byte[] bytes = ingestionClient.obtenerBytes(libroId);
        log.info("Archivo entregado exitosamente — libro: {}, tamaño: {} bytes",
                libroId, bytes.length);
        return bytes;
    }
}