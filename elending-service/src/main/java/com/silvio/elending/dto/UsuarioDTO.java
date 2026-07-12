package com.silvio.elending.dto;

import lombok.Data;

import java.util.Set;

// Representa la respuesta de Identity Service
// Contiene la información básica del usuario para validación en E-Lending
@Data
public class UsuarioDTO {

    private Long id;
    private String username;
    private Set<String> roles;
}
