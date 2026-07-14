package com.silvio.identity.dto;

import lombok.Data;
import lombok.ToString;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;

@Data
public class RegisterRequest {
    @NotBlank
    private String username;
    @NotBlank
    @ToString.Exclude
    private String password;
    private Set<String> roles; // ej: ["ROLE_USER"]
}
