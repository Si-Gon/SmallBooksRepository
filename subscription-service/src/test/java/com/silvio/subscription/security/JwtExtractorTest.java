package com.silvio.subscription.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitarios de JwtExtractor.
 *
 * JwtExtractor NO necesita Spring — es un @Component con lógica pura
 * de parseo de strings. Lo instanciamos directamente con "new".
 *
 * ¿Qué hace extraerUsuario()?
 * 1. Quita el prefijo "Bearer " (substring(7))
 * 2. Divide el token por "." → [header, payload, signature]
 * 3. Decodifica el payload en Base64
 * 4. Extrae el valor de "sub" del JSON con split simple
 *
 * Ejemplo real:
 *   Header: "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzaWx2aW8ifQ.sig"
 *   payload = Base64Url("{"sub":"silvio"}") = "eyJzdWIiOiJzaWx2aW8ifQ"
 *   resultado = "silvio"
 */
class JwtExtractorTest {

    // Instanciación directa — sin Spring, sin mocks, sin contexto
    private final JwtExtractor jwtExtractor = new JwtExtractor();

    /**
     * Construye un header Authorization con un payload JWT mínimo válido.
     * No es un JWT firmado real — solo tiene la estructura correcta
     * para que el parseo por substring/split funcione.
     */
    private String tokenFalso(String username) {
        // {"sub":"username","roles":"ROLE_USER"}
        String payloadJson = "{\"sub\":\"" + username + "\",\"roles\":\"ROLE_USER\"}";
        String payloadBase64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes());
        return "Bearer eyJhbGciOiJIUzI1NiJ9." + payloadBase64 + ".firma_falsa";
    }

    // =====================================================================
    // Casos exitosos
    // =====================================================================

    @Test
    void extraerUsuario_tokenValido_retornaUsername() {
        String resultado = jwtExtractor.extraerUsuario(tokenFalso("silvio"));
        assertThat(resultado).isEqualTo("silvio");
    }

    @Test
    void extraerUsuario_distintoUsername_retornaEseUsername() {
        assertThat(jwtExtractor.extraerUsuario(tokenFalso("ana"))).isEqualTo("ana");
        assertThat(jwtExtractor.extraerUsuario(tokenFalso("admin_user"))).isEqualTo("admin_user");
    }

    // =====================================================================
    // Casos de error — token malformado
    // =====================================================================

    @Test
    void extraerUsuario_tokenSinBearer_lanzaExcepcion() {
        // Sin el prefijo "Bearer ", substring(7) corta en el lugar equivocado
        assertThatThrownBy(() -> jwtExtractor.extraerUsuario("solo.el.token"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No se pudo extraer el usuario del token");
    }

    @Test
    void extraerUsuario_tokenSinPuntos_lanzaExcepcion() {
        // Sin separadores "." no se puede partir en [header, payload, signature]
        assertThatThrownBy(() -> jwtExtractor.extraerUsuario("Bearer tokensinpuntos"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No se pudo extraer el usuario del token");
    }

    @Test
    void extraerUsuario_payloadNoEsBase64_lanzaExcepcion() {
        // Payload con caracteres inválidos para Base64
        assertThatThrownBy(() -> jwtExtractor.extraerUsuario("Bearer header.!!!invalido!!!.sig"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No se pudo extraer el usuario del token");
    }

    @Test
    void extraerUsuario_payloadSinCampSub_lanzaExcepcion() {
        // Payload válido en Base64 pero sin el campo "sub"
        String payloadSinSub = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"roles\":\"ROLE_USER\"}".getBytes());
        assertThatThrownBy(() ->
                jwtExtractor.extraerUsuario("Bearer header." + payloadSinSub + ".sig"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No se pudo extraer el usuario del token");
    }
}
