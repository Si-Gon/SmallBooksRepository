package com.silvio.elending.client;

import com.silvio.elending.dto.SuscripcionDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "subscription-service")
public interface SubscriptionClient {

    // Consultar plan activo del usuario
    // E-Lending usa maxPrestamos y diasPrestamo para aplicar las reglas
    @GetMapping("/api/subscriptions/usuario/{usuarioId}")
    SuscripcionDTO obtenerSuscripcion(@PathVariable("usuarioId") String usuarioId);
}