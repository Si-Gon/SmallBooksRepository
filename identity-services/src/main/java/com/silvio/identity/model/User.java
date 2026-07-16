package com.silvio.identity.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    @ToString.Exclude
    private String password; // almacenar siempre el hash BCrypt

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    @ToString.Exclude
    private Set<String> roles; // ej: "ROLE_ADMIN", "ROLE_USER"

    // Hash SHA-256 del token de recuperación (nunca se almacena el token en texto plano)
    @ToString.Exclude
    private String resetTokenHash;
    private LocalDateTime resetTokenExpiry;

    // Hash SHA-256 del refresh token vigente para rotación
    @ToString.Exclude
    private String refreshTokenHash;
}
