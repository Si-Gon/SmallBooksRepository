package com.silvio.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {
    @NotBlank(message = "Refresh token es obligatorio")
    private String refreshToken;
}
