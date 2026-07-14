package com.silvio.identity.model;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica que los campos sensibles de User.java tengan @ToString.Exclude.
 *
 * Nota: Lombok usa RetentionPolicy.SOURCE, por lo que @ToString.Exclude
 * no es detectable via reflection. Usamos dos estrategias:
 * 1. Lectura del archivo fuente para verificar que la anotación existe
 * 2. Comportamiento runtime: toString() no debe contener los valores
 */
class SecurityModelTest {

    private static final Path USER_SOURCE = Paths.get(
            "src/main/java/com/silvio/identity/model/User.java");

    private String leerFuente() {
        try {
            return Files.readString(USER_SOURCE);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo leer " + USER_SOURCE, e);
        }
    }

    @Test
    void userEntity_debeTenerImportToString() {
        String source = leerFuente();
        assertTrue(source.contains("import lombok.ToString;"),
                "User.java debe importar lombok.ToString");
    }

    @Test
    void userEntity_debeTenerToStringExcludeEnPassword() {
        String source = leerFuente();
        assertTrue(source.contains("@ToString.Exclude"),
                "User.java debe contener @ToString.Exclude");
        // Verifica que está justo antes de password
        assertTrue(source.matches("(?s).*@ToString\\.Exclude\\s+private String password;.*"),
                "@ToString.Exclude debe estar antes de 'private String password;'");
    }

    @Test
    void userEntity_debeTenerToStringExcludeEnResetToken() {
        String source = leerFuente();
        assertTrue(source.matches("(?s).*@ToString\\.Exclude\\s+private String resetToken;.*"),
                "@ToString.Exclude debe estar antes de 'private String resetToken;'");
    }

    @Test
    void userEntity_debeTenerToStringExcludeEnRefreshTokenHash() {
        String source = leerFuente();
        assertTrue(source.matches("(?s).*@ToString\\.Exclude\\s+private String refreshTokenHash;.*"),
                "@ToString.Exclude debe estar antes de 'private String refreshTokenHash;'");
    }

    @Test
    void userEntity_toStringNoDebeContenerPassword() {
        User user = new User();
        user.setPassword("secret123");
        String toString = user.toString();
        assertFalse(toString.contains("secret123"),
                "toString() no debe contener el valor de password");
    }

    @Test
    void userEntity_toStringNoDebeContenerResetToken() {
        User user = new User();
        user.setResetToken("reset-token-value");
        String toString = user.toString();
        assertFalse(toString.contains("reset-token-value"),
                "toString() no debe contener el valor de resetToken");
    }

    @Test
    void userEntity_toStringNoDebeContenerRefreshTokenHash() {
        User user = new User();
        user.setRefreshTokenHash("sha256-hash-value");
        String toString = user.toString();
        assertFalse(toString.contains("sha256-hash-value"),
                "toString() no debe contener el valor de refreshTokenHash");
    }

    @Test
    void userEntity_toStringDebeContenerCamposNoSensibles() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        String toString = user.toString();
        assertTrue(toString.contains("id=1"),
                "toString() debe contener el campo 'id'");
        assertTrue(toString.contains("username=testuser"),
                "toString() debe contener el campo 'username'");
    }
}
