package com.silvio.content.client;

import com.silvio.content.dto.PrestamoDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(name = "elending-service", fallbackFactory = LendingClientFallbackFactory.class)
public interface LendingClient {

    // Obtener préstamos activos del usuario autenticado
    // Pasamos el usuarioId propagado por el Gateway
    @GetMapping("/api/lending/prestamos/activos")
    List<PrestamoDTO> obtenerPrestamosActivos(
            @RequestHeader("X-User-Id") String usuarioId);
}