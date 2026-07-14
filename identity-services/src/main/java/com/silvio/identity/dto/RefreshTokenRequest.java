package com.silvio.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

@Data
public class RefreshTokenRequest {
    @NotBlank(message = "Refresh token es obligatorio")
    @ToString.Exclude
    private String refreshToken;
}
