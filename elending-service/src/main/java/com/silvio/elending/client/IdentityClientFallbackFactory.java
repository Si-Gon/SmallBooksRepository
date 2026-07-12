package com.silvio.elending.client;

import com.silvio.elending.dto.UsuarioDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

// Fallback factory para IdentityClient
// Cuando el circuito está abierto o el servicio no responde,
// devuelve una respuesta degradada con datos del usuario desconocido.
@Slf4j
@Component
public class IdentityClientFallbackFactory implements FallbackFactory<IdentityClient> {

    @Override
    public IdentityClient create(Throwable cause) {
        log.warn("⛔ Identity Service no disponible. Activando fallback. Causa: {}",
                cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName());

        return new IdentityClient() {
            @Override
            public UsuarioDTO obtenerUsuario(String username) {
                log.warn("Fallback — obtenerUsuario({}) — devolviendo usuario por defecto", username);
                UsuarioDTO dto = new UsuarioDTO();
                dto.setId(0L);
                dto.setUsername(username);
                dto.setRoles(Set.of("ROLE_USER"));
                return dto;
            }
        };
    }
}
