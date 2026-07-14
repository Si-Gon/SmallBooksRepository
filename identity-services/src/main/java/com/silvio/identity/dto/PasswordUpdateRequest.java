package com.silvio.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

@Data
public class PasswordUpdateRequest {
    @NotBlank(message = "Token es obligatorio")
    @Size(min = 1, max = 500, message = "El token debe tener entre 1 y 500 caracteres")
    private String token;

    @NotBlank(message = "Nueva contraseña es obligatoria")
    @Size(min = 6, max = 100, message = "La nueva contraseña debe tener entre 6 y 100 caracteres")
    @ToString.Exclude
    private String newPassword;
}