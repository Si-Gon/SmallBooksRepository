package com.silvio.identity.dto;

import lombok.Data;

import java.util.Set;

// DTO público para consultar datos básicos del usuario
// Usado por E-Lending vía IdentityClient para validar usuarios
@Data
public class UsuarioDTO {

    private Long id;
    private String username;
    private Set<String> roles;
}
