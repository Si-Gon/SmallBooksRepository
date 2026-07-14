package com.silvio.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

@Data
public class ChangePasswordRequest {
    @NotBlank(message = "Contraseña actual es obligatoria")
    @Size(min = 1, max = 100, message = "La contraseña actual debe tener entre 1 y 100 caracteres")
    @ToString.Exclude
    private String currentPassword;

    @NotBlank(message = "Nueva contraseña es obligatoria")
    @Size(min = 6, max = 100, message = "La nueva contraseña debe tener entre 6 y 100 caracteres")
    @ToString.Exclude
    private String newPassword;
}