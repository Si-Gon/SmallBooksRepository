package com.silvio.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

@Data
public class PasswordUpdateRequest {
    @NotBlank(message = "Token es obligatorio")
    private String token;
    
    @NotBlank(message = "Nueva contraseña es obligatoria")
    @ToString.Exclude
    private String newPassword;
}