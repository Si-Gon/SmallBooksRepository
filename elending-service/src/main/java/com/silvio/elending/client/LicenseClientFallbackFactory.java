package com.silvio.elending.client;

import com.silvio.elending.dto.LicenciaDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

// Fallback factory para LicenseClient
// Cuando el circuito está abierto o el servicio no responde,
// devuelve respuestas degradadas: licencia sin copias disponibles (0).
// Esto evita préstamos cuando no se puede verificar disponibilidad.
@Slf4j
@Component
public class LicenseClientFallbackFactory implements FallbackFactory<LicenseClient> {

    @Override
    public LicenseClient create(Throwable cause) {
        log.warn(" License Service no disponible. Activando fallback. Causa: {}",
                cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName());

        return new LicenseClient() {
            @Override
            public LicenciaDTO obtenerLicencia(Long libroId) {
                log.warn("Fallback — obtenerLicencia({}) — devolviendo licencia sin copias disponibles", libroId);
                LicenciaDTO dto = new LicenciaDTO();
                dto.setLibroId(libroId);
                dto.setTotalCopias(0);
                dto.setCopiasDisponibles(0);
                return dto;
            }

            @Override
            public LicenciaDTO prestar(Long libroId) {
                log.warn("Fallback — prestar({}) — no se pudo descontar copia, devolviendo licencia degradada", libroId);
                LicenciaDTO dto = new LicenciaDTO();
                dto.setLibroId(libroId);
                dto.setTotalCopias(0);
                dto.setCopiasDisponibles(0);
                return dto;
            }

            @Override
            public LicenciaDTO devolver(Long libroId) {
                log.warn("Fallback — devolver({}) — no se pudo devolver copia, devolviendo licencia degradada", libroId);
                LicenciaDTO dto = new LicenciaDTO();
                dto.setLibroId(libroId);
                dto.setTotalCopias(0);
                dto.setCopiasDisponibles(0);
                return dto;
            }
        };
    }
}
