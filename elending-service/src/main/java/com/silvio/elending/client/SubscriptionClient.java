package com.silvio.elending.client;

import com.silvio.elending.dto.SuscripcionDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// Circuit Breaker habilitado vía spring.cloud.openfeign.circuitbreaker.enabled=true
// El nombre del cliente (subscription-service) se usa como ID del circuito en resilience4j
@FeignClient(
    name = "subscription-service",
    fallbackFactory = SubscriptionClientFallbackFactory.class
)
public interface SubscriptionClient {

    // Consultar plan activo del usuario
    // E-Lending usa maxPrestamos y diasPrestamo para aplicar las reglas
    // Circuit Breaker: si el servicio falla, el fallback devuelve plan BASICO
    @GetMapping("/api/subscriptions/usuario/{usuarioId}")
    SuscripcionDTO obtenerSuscripcion(@PathVariable("usuarioId") String usuarioId);
}