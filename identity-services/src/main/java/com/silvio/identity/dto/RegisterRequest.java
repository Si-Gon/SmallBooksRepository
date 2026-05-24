package com.silvio.identity.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;

@Data
public class RegisterRequest {
    @NotBlank
    private String username;
    @NotBlank
    private String password;
    private Set<String> roles; // ej: ["ROLE_USER"]
}
