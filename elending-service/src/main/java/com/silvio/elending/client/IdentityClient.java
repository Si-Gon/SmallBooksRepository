package com.silvio.elending.client;

import com.silvio.elending.dto.UsuarioDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// Circuit Breaker habilitado vía spring.cloud.openfeign.circuitbreaker.enabled=true
// El nombre del cliente (identity-service) se usa como ID del circuito en resilience4j
@FeignClient(
    name = "identity-service",
    fallbackFactory = IdentityClientFallbackFactory.class
)
public interface IdentityClient {

    // Consultar datos del usuario por username
    // Identity Service responde con la info básica del usuario
    @GetMapping("/api/users/{username}")
    UsuarioDTO obtenerUsuario(@PathVariable("username") String username);
}
