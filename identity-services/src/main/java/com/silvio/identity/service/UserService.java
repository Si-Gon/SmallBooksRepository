package com.silvio.identity.service;

import com.silvio.identity.config.JwtProperties;
import com.silvio.identity.dto.UsuarioDTO;
import com.silvio.identity.model.User;
import com.silvio.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.observation.annotation.Observed;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProperties jwtProperties;

    @Observed(name = "identity.registerUser")
    @Transactional
    public void registerUser(String username, String rawPassword, Set<String> roles) {
        log.info("Registrando nuevo usuario: {}", username);
        if (userRepository.findByUsername(username).isPresent()) {
            log.warn("Intento de registro con username ya existente: {}", username);
            throw new RuntimeException(" El usuario '" + username + "' ya existe");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRoles(roles != null && !roles.isEmpty() ? roles : Set.of("ROLE_USER"));
        userRepository.save(user);
        log.info("Usuario registrado exitosamente: {} con roles: {}", username, user.getRoles());
    }

    @Override
    @Observed(name = "identity.loadUserByUsername")
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("Cargando usuario para autenticación: {}", username);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("Usuario no encontrado: {}", username);
                    return new UsernameNotFoundException(" Usuario no encontrado: " + username);
                });

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(user.getRoles().stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList()))
                .build();
    }

    @Observed(name = "identity.createPasswordResetToken")
    @Transactional
    public String createPasswordResetToken(String username) {
        log.info("Generando token de recuperación para: {}", username);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException(" Usuario no encontrado"));

        String resetToken = UUID.randomUUID().toString();
        user.setResetToken(resetToken);
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(jwtProperties.getResetTokenExpirationHours()));
        userRepository.save(user);
        log.info("Token de recuperación generado para usuario: {}, expira en {} horas",
                username, jwtProperties.getResetTokenExpirationHours());
        return resetToken;
    }

    @Observed(name = "identity.resetPassword")
    @Transactional
    public void resetPassword(String token, String newPassword) {
        log.info("Intentando reset de contraseña con token");
        User user = userRepository.findByResetToken(token)
                .orElseThrow(() -> {
                    log.warn("Token de recuperación inválido");
                    return new RuntimeException(" Token de recuperación inválido");
                });

        if (user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            log.warn("Token de recuperación expirado para usuario: {}", user.getUsername());
            throw new RuntimeException(" El token de recuperación ha expirado");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
        log.info("Contraseña actualizada exitosamente para usuario: {}", user.getUsername());
    }

    @Observed(name = "identity.changePassword")
    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        log.info("Cambiando contraseña para usuario: {}", username);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException(" Usuario no encontrado"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            log.warn("Contraseña actual incorrecta para usuario: {}", username);
            throw new RuntimeException(" Contraseña actual incorrecta");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Contraseña cambiada exitosamente para usuario: {}", username);
    }

    // ─── Refresh Token Rotation ──────────────────────────────────────────────

    /**
     * Genera el hash SHA-256 de un refresh token para almacenamiento seguro.
     */
    private String hashRefreshToken(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("Error al generar hash del refresh token", e);
            throw new RuntimeException("Error interno de seguridad");
        }
    }

    @Observed(name = "identity.storeRefreshTokenHash")
    @Transactional
    public void storeRefreshTokenHash(String username, String refreshToken) {
        log.info("Almacenando hash de refresh token para usuario: {}", username);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException(" Usuario no encontrado"));
        user.setRefreshTokenHash(hashRefreshToken(refreshToken));
        userRepository.save(user);
    }

    /**
     * Valida que el refresh token coincida con el almacenado y lo rota.
     *
     * @return el nuevo hash almacenado si la validación es exitosa
     * @throws RuntimeException si el token no coincide (posible robo)
     */
    @Observed(name = "identity.rotateRefreshToken")
    @Transactional
    public void rotateRefreshToken(String oldRefreshToken, String newRefreshToken) {
        String oldHash = hashRefreshToken(oldRefreshToken);

        User user = userRepository.findByRefreshTokenHash(oldHash)
                .orElseThrow(() -> {
                    log.warn("Intento de refresco con token inválido o ya rotado — posible robo de token");
                    return new RuntimeException(" Refresh token inválido o ya utilizado");
                });

        String newHash = hashRefreshToken(newRefreshToken);
        user.setRefreshTokenHash(newHash);
        userRepository.save(user);
        log.info("Refresh token rotado exitosamente para usuario: {}", user.getUsername());
    }

    @Observed(name = "identity.obtenerUsuario")
    @Transactional(readOnly = true)
    public UsuarioDTO obtenerUsuarioPorUsername(String username) {
        log.info("Consultando usuario por username: {}", username);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("Usuario no encontrado: {}", username);
                    return new UsernameNotFoundException("Usuario '" + username + "' no encontrado");
                });

        UsuarioDTO dto = new UsuarioDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setRoles(user.getRoles());
        log.info("Usuario encontrado: {} con roles: {}", username, user.getRoles());
        return dto;
    }
}