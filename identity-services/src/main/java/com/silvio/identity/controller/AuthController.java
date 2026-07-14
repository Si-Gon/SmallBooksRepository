package com.silvio.identity.controller;

import com.silvio.identity.dto.*;
import com.silvio.identity.exception.TokenInvalidoException;
import com.silvio.identity.security.JwtUtil;
import com.silvio.identity.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;
import java.util.Map;

@Tag(name = "Identity", description = "Autenticación, registro y gestión de contraseñas — genera tokens JWT para acceso al sistema")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    @Operation(summary = "Iniciar sesión",
               description = "Autentica al usuario con username y password. " +
                             "Devuelve un access token (corta duración) y un refresh token (larga duración). " +
                             "El access token se usa en el header Authorization: Bearer {token}")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login exitoso — devuelve access y refresh token"),
        @ApiResponse(responseCode = "400", description = "Datos de login inválidos o incompletos"),
        @ApiResponse(responseCode = "401", description = "Credenciales incorrectas")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String accessToken = jwtUtil.generateAccessToken(userDetails);
        String refreshToken = jwtUtil.generateRefreshToken(userDetails.getUsername());
        // Almacenar hash del refresh token para poder rotarlo después
        userService.storeRefreshTokenHash(userDetails.getUsername(), refreshToken);
        AuthResponse response = new AuthResponse(
        accessToken, refreshToken,
        " Login exitoso. Bienvenido " + userDetails.getUsername(),
        userDetails.getUsername()
    );

    response.add(linkTo(methodOn(AuthController.class)
        .refreshToken(null)).withRel("refresh-token"));
    response.add(linkTo(methodOn(AuthController.class)
        .changePassword(null, null)).withRel("change-password"));

    return ResponseEntity.ok(response);
    }

    @Operation(summary = "Registrar usuario",
               description = "Crea una nueva cuenta de usuario con username, password y roles. " +
                             "Roles disponibles: ROLE_USER (plan BASICO por defecto), ROLE_PREMIUM, ROLE_LIBRARIAN")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Usuario registrado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos de registro inválidos"),
        @ApiResponse(responseCode = "409", description = "El username ya existe")
    })
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

    @Operation(summary = "Refrescar token",
               description = "Genera un nuevo access token usando un refresh token válido. " +
                             "Usar cuando el access token haya expirado sin necesidad de volver a loguearse")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Nuevo access token generado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Refresh token inválido o mal formado"),
        @ApiResponse(responseCode = "401", description = "Refresh token expirado o no es de tipo refresh")
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        // Verificar expiración primero antes de cualquier otra validación JWT
        if (jwtUtil.isTokenExpired(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthResponse(null, null, " Refresh token expirado", null));
        }

        String tokenType = jwtUtil.extractTokenType(refreshToken);
        if (!"refresh".equals(tokenType)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthResponse(null, null, " Token inválido: no es un refresh token", null));
        }
        String username = jwtUtil.extractUsername(refreshToken);
        // Validar contra el hash almacenado y rotar el token
        try {
            UserDetails userDetails = userService.loadUserByUsername(username);
            String newAccessToken = jwtUtil.generateAccessToken(userDetails);
            String newRefreshToken = jwtUtil.generateRefreshToken(username);
            // Rotar: invalida el viejo, almacena el nuevo hash
            userService.rotateRefreshToken(refreshToken, newRefreshToken);
            AuthResponse response = new AuthResponse(
                newAccessToken, newRefreshToken,
                " Token refrescado exitosamente", username
            );
            response.add(linkTo(methodOn(AuthController.class).login(null)).withRel("login"));
            return ResponseEntity.ok(response);
        } catch (TokenInvalidoException | UsernameNotFoundException e) {
            // Token inválido, ya rotado, usuario eliminado o posible robo — forzar re-login
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthResponse(null, null,
                    " Refresh token inválido o ya utilizado. Por favor inicia sesión nuevamente.", null));
        }
    }

    @Operation(summary = "Solicitar recuperación de contraseña",
               description = "Genera un token de recuperación para el username indicado. " +
                             "En producción este token se envía por email — " +
                             "en desarrollo se devuelve directamente en la respuesta")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token de recuperación generado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos o incompletos"),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado para el username indicado")
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody PasswordResetRequest request) {
        String resetToken = userService.createPasswordResetToken(request.getUsername());
        return ResponseEntity.ok(Map.of(
            "message", " Si el usuario existe, se ha generado un token de recuperación",
            "resetToken", resetToken,
            "instruction", "Usa este token en POST /auth/reset-password con tu nueva contraseña"
        ));
    }

    @Operation(summary = "Restablecer contraseña",
               description = "Actualiza la contraseña usando el token de recuperación obtenido en /forgot-password")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Contraseña actualizada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Nueva contraseña no válida"),
        @ApiResponse(responseCode = "401", description = "Token inválido, expirado o ya utilizado")
    })
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody PasswordUpdateRequest request) {
        userService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(Map.of(
            "message", " Contraseña actualizada exitosamente. Ya puedes iniciar sesión con tu nueva contraseña.",
            "status", "SUCCESS"
        ));
    }

    @Operation(summary = "Cambiar contraseña",
               description = "Cambia la contraseña del usuario autenticado. " +
                             "Requiere la contraseña actual como verificación de seguridad")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Contraseña cambiada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Contraseña actual incorrecta o nueva contraseña inválida"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente")
    })
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @Parameter(description = "Token JWT en formato: Bearer {token}", required = true)
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