package com.silvio.identity.controller;

import com.silvio.identity.dto.*;
import com.silvio.identity.security.JwtUtil;
import com.silvio.identity.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        
        String accessToken = jwtUtil.generateAccessToken(userDetails);
        String refreshToken = jwtUtil.generateRefreshToken(userDetails.getUsername());

        return ResponseEntity.ok(new AuthResponse(
            accessToken, 
            refreshToken, 
            " Login exitoso. Bienvenido " + userDetails.getUsername(),
            userDetails.getUsername()
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        userService.registerUser(request.getUsername(), request.getPassword(), request.getRoles());
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of(
                "message", " Usuario '" + request.getUsername() + "' registrado exitosamente",
                "username", request.getUsername(),
                "status", "CREATED"
            ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        
        // Validar que sea un refresh token
        String tokenType = jwtUtil.extractTokenType(refreshToken);
        if (!"refresh".equals(tokenType)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthResponse(null, null, " Token inválido: no es un refresh token", null));
        }
        
        String username = jwtUtil.extractUsername(refreshToken);
        
        // Verificar que el token no haya expirado
        if (jwtUtil.isTokenExpired(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthResponse(null, null, " Refresh token expirado", null));
        }
        
        // Generar nuevo access token
        UserDetails userDetails = userService.loadUserByUsername(username);
        String newAccessToken = jwtUtil.generateAccessToken(userDetails);
        String newRefreshToken = jwtUtil.generateRefreshToken(username);
        
        return ResponseEntity.ok(new AuthResponse(
            newAccessToken,
            newRefreshToken,
            " Token refrescado exitosamente",
            username
        ));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody PasswordResetRequest request) {
        String resetToken = userService.createPasswordResetToken(request.getUsername());
        
        // En producción, enviar este token por email
        // Por ahora lo devolvemos en la respuesta (solo para desarrollo)
        return ResponseEntity.ok(Map.of(
            "message", " Si el usuario existe, se ha generado un token de recuperación",
            "resetToken", resetToken, // En producción NO devolver esto, solo enviar por email
            "instruction", "Usa este token en POST /auth/reset-password con tu nueva contraseña"
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody PasswordUpdateRequest request) {
        userService.resetPassword(request.getToken(), request.getNewPassword());
        
        return ResponseEntity.ok(Map.of(
            "message", " Contraseña actualizada exitosamente. Ya puedes iniciar sesión con tu nueva contraseña.",
            "status", "SUCCESS"
        ));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody ChangePasswordRequest request) {
        
        String token = authHeader.substring(7);
        String username = jwtUtil.extractUsername(token);
        
        userService.changePassword(username, request.getCurrentPassword(), request.getNewPassword());
        
        return ResponseEntity.ok(Map.of(
            "message", " Contraseña cambiada exitosamente",
            "status", "SUCCESS"
        ));
    }
}