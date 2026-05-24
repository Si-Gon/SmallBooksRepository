package com.silvio.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PasswordUpdateRequest {
    @NotBlank(message = "Token es obligatorio")
    private String token;
    
    @NotBlank(message = "Nueva contraseña es obligatoria")
    private String newPassword;
}