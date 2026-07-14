package com.silvio.identity.dto;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica que los DTOs de respuesta no expongan campos sensibles
 * y que los DTOs de entrada tengan @ToString.Exclude en campos sensibles.
 *
 * Lombok usa RetentionPolicy.SOURCE, por lo que las anotaciones no son
 * detectables via reflection. Usamos lectura de fuente + comportamiento runtime.
 */
class DTOSecurityTest {

    private static final Path DTO_DIR = Paths.get("src/main/java/com/silvio/identity/dto/");

    // =========================================================
    // Response DTOs — No deben exponer campos sensibles
    // =========================================================

    @Test
    void usuarioDTO_noDebeTenerPassword() {
        assertDtoNoTieneCampo(UsuarioDTO.class, "password");
    }

    @Test
    void usuarioDTO_noDebeTenerResetToken() {
        assertDtoNoTieneCampo(UsuarioDTO.class, "resetToken");
    }

    @Test
    void usuarioDTO_soloTieneCamposEsperados() {
        List<String> camposEsperados = List.of("id", "username", "roles");
        List<String> camposReales = Arrays.stream(UsuarioDTO.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .toList();
        assertTrue(camposReales.containsAll(camposEsperados),
                "UsuarioDTO debe tener: " + camposEsperados + " pero tiene: " + camposReales);
        assertEquals(camposEsperados.size(), camposReales.size(),
                "UsuarioDTO no debe tener campos adicionales. Encontrados: " + camposReales);
    }

    @Test
    void authResponse_noDebeTenerPassword() {
        assertDtoNoTieneCampo(AuthResponse.class, "password");
    }

    @Test
    void authResponse_noDebeTenerResetToken() {
        assertDtoNoTieneCampo(AuthResponse.class, "resetToken");
    }

    @Test
    void authResponse_debeTenerRefreshToken() {
        // refreshToken se expone intencionalmente para el flujo de refresh
        assertTieneCampo(AuthResponse.class, "refreshToken",
                "AuthResponse debe exponer refreshToken intencionalmente para el flujo de refresh");
    }

    @Test
    void authResponse_tieneCamposEsperados() {
        List<String> camposReales = Arrays.stream(AuthResponse.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .toList();
        assertTrue(camposReales.contains("accessToken"),
                "AuthResponse debe tener accessToken");
        assertTrue(camposReales.contains("refreshToken"),
                "AuthResponse debe tener refreshToken (intencional)");
        assertTrue(camposReales.contains("message"),
                "AuthResponse debe tener message");
        assertTrue(camposReales.contains("username"),
                "AuthResponse debe tener username");
    }

    @Test
    void ningunResponseDTOEnIdentityDebeTenerCamposProhibidos() {
        List<Class<?>> responseDTOs = List.of(
                UsuarioDTO.class,
                AuthResponse.class
        );
        List<String> prohibidos = List.of("password", "resetToken");

        for (Class<?> dtoClass : responseDTOs) {
            for (java.lang.reflect.Field field : dtoClass.getDeclaredFields()) {
                for (String prohibido : prohibidos) {
                    assertNotEquals(prohibido, field.getName(),
                            "El DTO " + dtoClass.getSimpleName()
                                    + " no debe tener el campo '" + prohibido + "'");
                }
            }
        }
    }

    // =========================================================
    // Request DTOs — Deben tener @ToString.Exclude en passwords/tokens
    // (verificación via fuente ya que Lombok tiene SOURCE retention)
    // =========================================================

    @Test
    void authRequest_debeTenerExcludeEnPassword() {
        assertFuenteTieneExcludeAntesDe("AuthRequest.java", "private String password;");
    }

    @Test
    void registerRequest_debeTenerExcludeEnPassword() {
        assertFuenteTieneExcludeAntesDe("RegisterRequest.java", "private String password;");
    }

    @Test
    void changePasswordRequest_debeTenerExcludeEnCurrentPassword() {
        assertFuenteTieneExcludeAntesDe("ChangePasswordRequest.java", "private String currentPassword;");
    }

    @Test
    void changePasswordRequest_debeTenerExcludeEnNewPassword() {
        assertFuenteTieneExcludeAntesDe("ChangePasswordRequest.java", "private String newPassword;");
    }

    @Test
    void passwordUpdateRequest_debeTenerExcludeEnNewPassword() {
        assertFuenteTieneExcludeAntesDe("PasswordUpdateRequest.java", "private String newPassword;");
    }

    @Test
    void refreshTokenRequest_debeTenerExcludeEnRefreshToken() {
        assertFuenteTieneExcludeAntesDe("RefreshTokenRequest.java", "private String refreshToken;");
    }

    // =========================================================
    // Verificacion runtime — toString() no debe filtrar valores
    // =========================================================

    @Test
    void authRequest_toStringNoDebeContenerPassword() {
        AuthRequest request = new AuthRequest();
        request.setUsername("testuser");
        request.setPassword("mi-contrasena-secreta");
        String toString = request.toString();
        assertFalse(toString.contains("mi-contrasena-secreta"),
                "toString() de AuthRequest no debe contener la contraseña");
        assertTrue(toString.contains("testuser"),
                "toString() debe contener el username (no sensible)");
    }

    @Test
    void registerRequest_toStringNoDebeContenerPassword() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("nuevousuario");
        request.setPassword("Password123!");
        String toString = request.toString();
        assertFalse(toString.contains("Password123!"),
                "toString() de RegisterRequest no debe contener la contraseña");
    }

    @Test
    void changePasswordRequest_toStringNoDebeContenerPasswords() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("currentPass123");
        request.setNewPassword("newPass456");
        String toString = request.toString();
        assertFalse(toString.contains("currentPass123"),
                "toString() no debe contener currentPassword");
        assertFalse(toString.contains("newPass456"),
                "toString() no debe contener newPassword");
    }

    @Test
    void passwordUpdateRequest_toStringNoDebeContenerNewPassword() {
        PasswordUpdateRequest request = new PasswordUpdateRequest();
        request.setToken("valid-token");
        request.setNewPassword("NuevaPassword123!");
        String toString = request.toString();
        assertFalse(toString.contains("NuevaPassword123!"),
                "toString() no debe contener newPassword");
    }

    @Test
    void refreshTokenRequest_toStringNoDebeContenerRefreshToken() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("mi-refresh-token-secreto");
        String toString = request.toString();
        assertFalse(toString.contains("mi-refresh-token-secreto"),
                "toString() de RefreshTokenRequest no debe contener el refresh token");
    }

    // =========================================================
    // Metodos auxiliares
    // =========================================================

    private void assertDtoNoTieneCampo(Class<?> dtoClass, String nombreCampo) {
        java.lang.reflect.Field[] fields = dtoClass.getDeclaredFields();
        boolean encontrado = Arrays.stream(fields)
                .anyMatch(f -> f.getName().equals(nombreCampo));
        assertFalse(encontrado,
                "El DTO " + dtoClass.getSimpleName() + " no debe tener el campo '" + nombreCampo + "'");
    }

    private void assertTieneCampo(Class<?> dtoClass, String nombreCampo, String mensaje) {
        java.lang.reflect.Field[] fields = dtoClass.getDeclaredFields();
        boolean encontrado = Arrays.stream(fields)
                .anyMatch(f -> f.getName().equals(nombreCampo));
        assertTrue(encontrado, mensaje);
    }

    /**
     * Lee el archivo fuente DTO y verifica que @ToString.Exclude esté
     * presente justo antes del campo indicado.
     * Lombok tiene RetentionPolicy.SOURCE, por lo que la anotación no
     * está disponible via reflection en runtime.
     */
    private void assertFuenteTieneExcludeAntesDe(String nombreArchivo, String patronCampo) {
        Path archivo = DTO_DIR.resolve(nombreArchivo);
        assertTrue(Files.exists(archivo), "El archivo " + nombreArchivo + " debe existir en " + DTO_DIR);
        try {
            String source = Files.readString(archivo);
            assertTrue(source.matches("(?s).*@ToString\\.Exclude\\s+" + java.util.regex.Pattern.quote(patronCampo) + ".*"),
                    "@ToString.Exclude debe estar antes de '" + patronCampo + "' en " + nombreArchivo);
        } catch (IOException e) {
            fail("No se pudo leer " + archivo + ": " + e.getMessage());
        }
    }
}
