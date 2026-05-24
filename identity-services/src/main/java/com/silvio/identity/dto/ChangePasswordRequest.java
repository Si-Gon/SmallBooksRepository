package com.silvio.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangePasswordRequest {
    @NotBlank(message = "Contraseña actual es obligatoria")
    private String currentPassword;
    
    @NotBlank(message = "Nueva contraseña es obligatoria")
    private String newPassword;
}