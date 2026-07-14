package com.silvio.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

@Data
public class RefreshTokenRequest {
    @NotBlank(message = "Refresh token es obligatorio")
    @Size(min = 10, max = 500, message = "El refresh token debe tener entre 10 y 500 caracteres")
    @ToString.Exclude
    private String refreshToken;
}
