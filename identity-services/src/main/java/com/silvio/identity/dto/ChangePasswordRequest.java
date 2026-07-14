package com.silvio.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

@Data
public class ChangePasswordRequest {
    @NotBlank(message = "Contraseña actual es obligatoria")
    @ToString.Exclude
    private String currentPassword;
    
    @NotBlank(message = "Nueva contraseña es obligatoria")
    @ToString.Exclude
    private String newPassword;
}