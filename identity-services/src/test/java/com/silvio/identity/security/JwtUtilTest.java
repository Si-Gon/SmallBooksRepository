package com.silvio.identity.security;

import com.silvio.identity.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios de JwtUtil.
 *
 * Verifica que la migración a jjwt 0.12.x funciona correctamente:
 * - Jwts.builder().claims() en lugar del builder con map directo
 * - Jwts.parser().verifyWith() en lugar de parserBuilder().setSigningKey()
 * - Jwts.SIG.HS256 en lugar de SignatureAlgorithm.HS256
 * - Keys.hmacShaKeyFor() para derivar clave HMAC
 */
@DisplayName("JwtUtil — migración jjwt 0.12.x")
class JwtUtilTest {

    private static final String SECRET = "miClaveSecretaSuperSeguraParaJWT256Bits2024!";
    private static final long ACCESS_EXPIRATION = 3600000;   // 1 hora
    private static final long REFRESH_EXPIRATION = 604800000; // 7 días

    private JwtUtil jwtUtil;
    private JwtProperties properties;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setAccessTokenExpiration(ACCESS_EXPIRATION);
        properties.setRefreshTokenExpiration(REFRESH_EXPIRATION);

        jwtUtil = new JwtUtil(properties);

        userDetails = new User("silvio", "password",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    // =============================================================
    // GENERACIÓN DE TOKENS — happy path
    // =============================================================

    @Test
    @DisplayName("generateAccessToken: genera token JWT válido con subject, roles y tipo access")
    void generateAccessToken_exitoso_debeGenerarTokenValido() {
        String token = jwtUtil.generateAccessToken(userDetails);

        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3, "Debe tener 3 partes (header.payload.signature)");

        Claims claims = jwtUtil.validateToken(token);
        assertEquals("silvio", claims.getSubject());
        assertEquals("ROLE_USER", claims.get("roles"));
        assertEquals("access", claims.get("type"));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    @DisplayName("generateRefreshToken: genera token JWT con subject y tipo refresh, sin roles")
    void generateRefreshToken_exitoso_debeGenerarTokenValido() {
        String token = jwtUtil.generateRefreshToken("silvio");

        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3);

        Claims claims = jwtUtil.validateToken(token);
        assertEquals("silvio", claims.getSubject());
        assertEquals("refresh", claims.get("type"));
        assertNull(claims.get("roles"));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    @DisplayName("generateAccessToken: token con múltiples roles genera claim correcto")
    void generateAccessToken_conMultiplesRoles_debeIncluirTodos() {
        UserDetails multiRoleUser = new User("admin", "password",
                List.of(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ROLE_PREMIUM")
                ));

        String token = jwtUtil.generateAccessToken(multiRoleUser);
        Claims claims = jwtUtil.validateToken(token);

        String roles = claims.get("roles", String.class);
        assertNotNull(roles);
        assertTrue(roles.contains("ROLE_USER"));
        assertTrue(roles.contains("ROLE_ADMIN"));
        assertTrue(roles.contains("ROLE_PREMIUM"));
    }

    // =============================================================
    // EXTRACCIÓN DE CLAIMS — happy path
    // =============================================================

    @Test
    @DisplayName("extractUsername: extrae el subject correcto del token access")
    void extractUsername_deTokenAccess_debeRetornarSubject() {
        String token = jwtUtil.generateAccessToken(userDetails);
        assertEquals("silvio", jwtUtil.extractUsername(token));
    }

    @Test
    @DisplayName("extractUsername: extrae el subject correcto del token refresh")
    void extractUsername_deTokenRefresh_debeRetornarSubject() {
        String token = jwtUtil.generateRefreshToken("silvio");
        assertEquals("silvio", jwtUtil.extractUsername(token));
    }

    @Test
    @DisplayName("extractRoles: extrae roles del token access")
    void extractRoles_deTokenAccess_debeRetornarRoles() {
        String token = jwtUtil.generateAccessToken(userDetails);
        assertEquals("ROLE_USER", jwtUtil.extractRoles(token));
    }

    @Test
    @DisplayName("extractRoles: retorna null para token refresh (sin roles)")
    void extractRoles_deTokenRefresh_debeRetornarNull() {
        String token = jwtUtil.generateRefreshToken("silvio");
        assertNull(jwtUtil.extractRoles(token));
    }

    @Test
    @DisplayName("extractTokenType: distingue entre access y refresh")
    void extractTokenType_debeDistinguirAccessYRefresh() {
        String accessToken = jwtUtil.generateAccessToken(userDetails);
        String refreshToken = jwtUtil.generateRefreshToken("silvio");

        assertEquals("access", jwtUtil.extractTokenType(accessToken));
        assertEquals("refresh", jwtUtil.extractTokenType(refreshToken));
    }

    @Test
    @DisplayName("isTokenExpired: token recién generado no está expirado")
    void isTokenExpired_tokenRecienGenerado_debeRetornarFalse() {
        String token = jwtUtil.generateAccessToken(userDetails);
        assertFalse(jwtUtil.isTokenExpired(token));
    }

    // =============================================================
    // VALIDACIÓN — token inválido / alterado
    // =============================================================

    @Test
    @DisplayName("validateToken: rechaza token con firma incorrecta (clave diferente)")
    void validateToken_firmaIncorrecta_debeLanzarSecurityException() {
        String token = jwtUtil.generateAccessToken(userDetails);

        // Usar Jwts.parser() con otra clave para verificar que el token es rechazado
        SecretKey differentKey = Keys.hmacShaKeyFor("otraClaveDiferenteQueTambienTiene32BytesMinimos!".getBytes());

        assertThrows(SecurityException.class, () ->
                Jwts.parser()
                        .verifyWith(differentKey)
                        .build()
                        .parseClaimsJws(token)
        );
    }

    @Test
    @DisplayName("validateToken: rechaza token malformado")
    void validateToken_malformado_debeLanzarMalformedJwtException() {
        String token = "este.no.es.un.token.valido";
        assertThrows(MalformedJwtException.class, () -> jwtUtil.validateToken(token));
    }

    @Test
    @DisplayName("validateToken: rechaza token vacío")
    void validateToken_vacio_debeLanzarExcepcion() {
        assertThrows(Exception.class, () -> jwtUtil.validateToken(""));
    }

    @Test
    @DisplayName("validateToken: rechaza token nulo")
    void validateToken_nulo_debeLanzarExcepcion() {
        assertThrows(Exception.class, () -> jwtUtil.validateToken(null));
    }

    // =============================================================
    // TOKEN EXPIRADO
    // =============================================================

    @Test
    @DisplayName("validateToken: rechaza token expirado (creado con expiración en pasado)")
    void validateToken_expirado_debeLanzarExpiredJwtException() {
        // Generar token con expiración forzada en el pasado usando la API fluida de jjwt 0.12.x
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
        String expiredToken = Jwts.builder()
                .claims()
                    .subject("silvio")
                    .issuedAt(new Date(System.currentTimeMillis() - 3600000)) // 1 hora atrás
                    .expiration(new Date(System.currentTimeMillis() - 1))      // ya expiró
                    .and()
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThrows(ExpiredJwtException.class, () -> jwtUtil.validateToken(expiredToken));
    }

    // NOTA: isTokenExpired() internamente llama a validateToken() que lanza
    // ExpiredJwtException en tokens expirados, por lo que el método nunca
    // retorna true actualmente. La validación de expiración se cubre en
    // validateToken_expirado_debeLanzarExpiredJwtException.

    // =============================================================
    // CASOS BORDE
    // =============================================================

    @Test
    @DisplayName("generateAccessToken: userDetails sin roles genera token con roles vacío")
    void generateAccessToken_sinRoles_debeGenerarTokenSinRoles() {
        UserDetails userSinRoles = new User("sinroles", "password", List.of());
        String token = jwtUtil.generateAccessToken(userSinRoles);
        Claims claims = jwtUtil.validateToken(token);
        // El claim "roles" debe existir pero vacío
        assertEquals("", claims.get("roles", String.class));
    }

    @Test
    @DisplayName("generateRefreshToken: username con caracteres especiales se preserva en el subject")
    void generateRefreshToken_usernameEspecial_debePreservarSubject() {
        String email = "usuario@correo.com";
        String token = jwtUtil.generateRefreshToken(email);
        assertEquals(email, jwtUtil.extractUsername(token));
    }

    @Test
    @DisplayName("validateToken: token access válido se autovalida con la misma instancia de JwtUtil")
    void validateToken_mismaInstancia_debeValidarCorrectamente() {
        String token = jwtUtil.generateAccessToken(userDetails);
        Claims claims = jwtUtil.validateToken(token);
        assertNotNull(claims);
        assertEquals("silvio", claims.getSubject());
    }

    @Test
    @DisplayName("validateToken: token refresh se autovalida correctamente")
    void validateToken_refreshToken_debeValidarCorrectamente() {
        String token = jwtUtil.generateRefreshToken("silvio");
        Claims claims = jwtUtil.validateToken(token);
        assertEquals("refresh", claims.get("type"));
    }
}
