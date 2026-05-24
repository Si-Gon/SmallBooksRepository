package com.silvio.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PasswordResetRequest {
    @NotBlank(message = "Username es obligatorio")
    private String username;
}