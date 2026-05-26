package com.silvio.content.client;

import com.silvio.content.dto.PrestamoDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(name = "elending-service")
public interface LendingClient {

    // Obtener préstamos activos del usuario autenticado
    // Pasamos el token para que E-Lending extraiga el usuarioId
    @GetMapping("/api/lending/prestamos/activos")
    List<PrestamoDTO> obtenerPrestamosActivos(
            @RequestHeader("Authorization") String authHeader);
}