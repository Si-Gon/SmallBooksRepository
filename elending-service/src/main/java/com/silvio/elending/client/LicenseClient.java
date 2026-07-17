package com.silvio.elending.client;

import com.silvio.elending.dto.LicenciaDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;

// lb://license-service → Feign usa Eureka para encontrar la URL real
// No necesitamos saber el puerto — Eureka lo resuelve por nombre
@FeignClient(
        name = "license-service",
        fallbackFactory = LicenseClientFallbackFactory.class
)
public interface LicenseClient {

    // Consultar copias disponibles de un libro
    @GetMapping("/api/licenses/{libroId}")
    LicenciaDTO obtenerLicencia(@PathVariable("libroId") Long libroId);

    // Descontar 1 copia al crear préstamo
    @PatchMapping("/api/licenses/{libroId}/prestar")
    LicenciaDTO prestar(@PathVariable("libroId") Long libroId);

    // Devolver 1 copia al vencer préstamo
    @PatchMapping("/api/licenses/{libroId}/devolver")
    LicenciaDTO devolver(@PathVariable("libroId") Long libroId);
}