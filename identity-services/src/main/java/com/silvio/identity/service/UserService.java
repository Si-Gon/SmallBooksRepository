package com.silvio.identity.service;

import com.silvio.identity.config.JwtProperties;
import com.silvio.identity.dto.AuthRequest;
import com.silvio.identity.dto.AuthResponse;
import com.silvio.identity.dto.RefreshTokenRequest;
import com.silvio.identity.dto.UsuarioDTO;
import com.silvio.identity.model.User;
import com.silvio.identity.exception.ContrasenaIncorrectaException;
import com.silvio.identity.exception.ErrorSeguridadException;
import com.silvio.identity.exception.TokenExpiradoException;
import com.silvio.identity.exception.TokenInvalidoException;
import com.silvio.identity.exception.UsuarioDuplicadoException;
import com.silvio.identity.exception.UsuarioNotFoundException;
import com.silvio.identity.repository.UserRepository;
import com.silvio.identity.security.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProperties jwtProperties;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtProperties jwtProperties,
                       @Lazy AuthenticationManager authenticationManager,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProperties = jwtProperties;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    @Observed(name = "identity.registerUser")
    @Transactional
    public void registerUser(String username, String rawPassword) {
        log.info("Registrando nuevo usuario: {}", username);
        if (userRepository.findByUsername(username).isPresent()) {
            log.warn("Intento de registro con username ya existente: {}", username);
            throw new UsuarioDuplicadoException(username);
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        // Siempre asignar ROLE_USER por defecto — nunca aceptar roles del cliente
        user.setRoles(Set.of("ROLE_USER"));
        userRepository.save(user);
        log.info("Usuario registrado exitosamente: {} con roles: {}", username, user.getRoles());
    }

    @Observed(name = "identity.login")
    @Transactional
    public AuthResponse login(AuthRequest request) {
        log.info("Autenticando usuario: {}", request.getUsername());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String accessToken = jwtUtil.generateAccessToken(userDetails);
        String refreshToken = jwtUtil.generateRefreshToken(userDetails.getUsername());
        // Almacenar hash del refresh token para poder rotarlo después
        storeRefreshTokenHash(userDetails.getUsername(), refreshToken);
        log.info("Login exitoso para: {}", userDetails.getUsername());
        return new AuthResponse(accessToken, refreshToken,
                " Login exitoso. Bienvenido " + userDetails.getUsername(),
                userDetails.getUsername());
    }

    @Observed(name = "identity.refreshToken")
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        log.info("Procesando solicitud de refresco de token");

        // Verificar expiración primero antes de cualquier otra validación JWT
        if (jwtUtil.isTokenExpired(refreshToken)) {
            log.warn("Refresh token expirado");
            throw new TokenExpiradoException(" Refresh token expirado");
        }

        String tokenType = jwtUtil.extractTokenType(refreshToken);
        if (!"refresh".equals(tokenType)) {
            log.warn("Token inválido: no es un refresh token");
            throw new TokenInvalidoException(" Token inválido: no es un refresh token");
        }

        String username = jwtUtil.extractUsername(refreshToken);
        try {
            UserDetails userDetails = loadUserByUsername(username);
            String newAccessToken = jwtUtil.generateAccessToken(userDetails);
            String newRefreshToken = jwtUtil.generateRefreshToken(username);
            // Rotar: invalida el viejo, almacena el nuevo hash
            rotateRefreshToken(refreshToken, newRefreshToken);
            log.info("Token refrescado exitosamente para: {}", username);
            return new AuthResponse(newAccessToken, newRefreshToken,
                    " Token refrescado exitosamente", username);
        } catch (TokenInvalidoException | UsernameNotFoundException e) {
            // Token inválido, ya rotado, usuario eliminado o posible robo — forzar re-login
            log.warn("Refresh token inválido o ya utilizado para: {}", username);
            throw new TokenInvalidoException(
                    " Refresh token inválido o ya utilizado. Por favor inicia sesión nuevamente.");
        }
    }

    @Override
    @Observed(name = "identity.loadUserByUsername")
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("Cargando usuario para autenticación: {}", username);
        User user = userRepository.findByUsernameWithRoles(username)
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
        // Si el usuario no existe, retornar null sin lanzar excepción
        // para no revelar información sobre la existencia del usuario
        java.util.Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            log.warn("Intento de recuperación para usuario inexistente: {}", username);
            return null;
        }
        User user = userOpt.get();

        String resetToken = UUID.randomUUID().toString();
        // Almacenar solo el hash SHA-256, nunca el token en texto plano
        user.setResetTokenHash(hashToken(resetToken));
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
        String tokenHash = hashToken(token);
        User user = userRepository.findByResetTokenHash(tokenHash)
                .orElseThrow(() -> {
                    log.warn("Token de recuperación inválido");
                    return new TokenInvalidoException();
                });

        if (user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            log.warn("Token de recuperación expirado para usuario: {}", user.getUsername());
            throw new TokenExpiradoException();
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetTokenHash(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
        log.info("Contraseña actualizada exitosamente para usuario: {}", user.getUsername());
    }

    @Observed(name = "identity.changePassword")
    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        log.info("Cambiando contraseña para usuario: {}", username);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsuarioNotFoundException(username));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            log.warn("Contraseña actual incorrecta para usuario: {}", username);
            throw new ContrasenaIncorrectaException();
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Contraseña cambiada exitosamente para usuario: {}", username);
    }

    /**
     * Cambia la contraseña del usuario autenticado extrayendo el username desde el token JWT.
     * Sigue el patrón CSR: el Controller delega toda la lógica de negocio al Service.
     */
    @Observed(name = "identity.changePasswordFromToken")
    @Transactional
    public void changePasswordFromToken(String authHeader, String currentPassword, String newPassword) {
        String token = authHeader.substring(7);
        String username = jwtUtil.extractUsername(token);
        changePassword(username, currentPassword, newPassword);
    }

    // ─── Refresh Token Rotation ──────────────────────────────────────────────

    /**
     * Genera el hash SHA-256 de un token para almacenamiento seguro.
     * Usado tanto para refresh tokens como para tokens de recuperación.
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("Error al generar hash del token", e);
            throw new ErrorSeguridadException("Error interno de seguridad");
        }
    }

    @Observed(name = "identity.storeRefreshTokenHash")
    @Transactional
    public void storeRefreshTokenHash(String username, String refreshToken) {
        log.info("Almacenando hash de refresh token para usuario: {}", username);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsuarioNotFoundException(username));
        user.setRefreshTokenHash(hashToken(refreshToken));
        userRepository.save(user);
    }

    /**
     * Valida que el refresh token coincida con el almacenado y lo rota.
     *
     * @return el nuevo hash almacenado si la validación es exitosa
     * @throws TokenInvalidoException si el token no coincide (posible robo)
     */
    @Observed(name = "identity.rotateRefreshToken")
    @Transactional
    public void rotateRefreshToken(String oldRefreshToken, String newRefreshToken) {
        String oldHash = hashToken(oldRefreshToken);

        User user = userRepository.findByRefreshTokenHash(oldHash)
                .orElseThrow(() -> {
                    log.warn("Intento de refresco con token inválido o ya rotado — posible robo de token");
                    return new TokenInvalidoException();
                });

        String newHash = hashToken(newRefreshToken);
        user.setRefreshTokenHash(newHash);
        userRepository.save(user);
        log.info("Refresh token rotado exitosamente para usuario: {}", user.getUsername());
    }

    @Observed(name = "identity.obtenerUsuario")
    @Transactional(readOnly = true)
    public UsuarioDTO obtenerUsuarioPorUsername(String username) {
        log.info("Consultando usuario por username: {}", username);
        User user = userRepository.findByUsernameWithRoles(username)
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