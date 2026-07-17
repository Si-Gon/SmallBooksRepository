package com.silvio.identity.controller;

import com.silvio.identity.dto.UsuarioDTO;
import com.silvio.identity.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Users", description = "Consulta de datos de usuarios — usado por otros servicios via Feign Client")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Obtener usuario por username", security = @SecurityRequirement(name = "BearerAuth"),
               description = "Devuelve la información básica del usuario (id, username, roles). " +
                             "Usado por E-Lending Service para validar usuarios al crear préstamos.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuario encontrado — devuelve id, username y roles"),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado para el username indicado"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente")
    })
    @GetMapping("/{username}")
    public ResponseEntity<UsuarioDTO> obtenerUsuario(
            @Parameter(description = "Username del usuario a consultar", required = true)
            @PathVariable("username") String username) {
        UsuarioDTO dto = userService.obtenerUsuarioPorUsername(username);
        return ResponseEntity.ok(dto);
    }
}
