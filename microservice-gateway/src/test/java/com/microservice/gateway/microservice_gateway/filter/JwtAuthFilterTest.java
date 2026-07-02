package com.microservice.gateway.microservice_gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Field;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios del JwtAuthFilter.
 *
 * ¿Por qué esta clase es especial?
 * JwtAuthFilter es un filtro de Spring Cloud Gateway (reactivo/WebFlux).
 * A diferencia de los filtros Servlet normales (que usan HttpServletRequest),
 * este opera con ServerWebExchange — el tipo reactivo equivalente.
 *
 * No usamos @WebFluxTest porque cargar el contexto Gateway completo
 * requeriría rutas configuradas y Eureka. En su lugar, construimos
 * los objetos del mundo reactivo manualmente:
 *
 * - MockServerHttpRequest: simula una petición HTTP reactiva
 * - MockServerWebExchange: simula el "exchange" (request + response) reactivo
 * - GatewayFilterChain: se mockea para simular el "siguiente paso" en la cadena
 *
 * Estrategia para inyectar el secret (@Value):
 * @Value no funciona fuera de un contexto Spring. Usamos reflexión (Field.set)
 * para inyectar el valor directamente en el campo privado "secret".
 * Esto es una técnica estándar para testear beans con @Value sin levantar Spring.
 *
 * Tokens JWT reales:
 * En lugar de tokens falsos, generamos tokens REALES con la misma librería JJWT
 * que usa el filtro en producción. Así el test valida el comportamiento exacto.
 */
class JwtAuthFilterTest {

    private static final String SECRET = "clave-secreta-de-prueba-para-tests-unitarios-32chars";

    private JwtAuthFilter filter;
    private GatewayFilterChain chain;
    private Key signingKey;

    @BeforeEach
    void setUp() throws Exception {
        filter = new JwtAuthFilter();

        // Inyectamos el secret via reflexión porque @Value no funciona fuera de Spring
        // Field.setAccessible(true) permite acceder a campos privados desde fuera de la clase
        Field secretField = JwtAuthFilter.class.getDeclaredField("secret");
        secretField.setAccessible(true);
        secretField.set(filter, SECRET);

        // La misma clave que usará el filtro internamente
        signingKey = Keys.hmacShaKeyFor(SECRET.getBytes());

        // Mock de la cadena de filtros — cuando se llama filter(exchange), devuelve Mono.empty()
        // Mono.empty() equivale a "completado exitosamente sin valor" — el request pasó
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    // ─── Helper: genera un JWT real con los claims indicados ─────────────────

    private String generarToken(String tipo, int expirationMs) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", tipo);
        claims.put("roles", "ROLE_USER");

        return Jwts.builder()
                .setClaims(claims)
                .setSubject("silvio")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    // ─── Caso 1: token access válido → debe pasar (200) ──────────────────────

    @Test
    void tokenAccessValido_debePasar() {
        String jwt = generarToken("access", 60_000); // expira en 1 minuto

        // MockServerHttpRequest construye una petición HTTP reactiva simulada
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Ejecutamos el filtro y verificamos con StepVerifier (herramienta de Reactor Test)
        // StepVerifier.create() → "voy a verificar este Mono"
        // .verifyComplete()    → "espero que complete sin error y sin emitir valores"
        var gatewayFilter = filter.apply(new JwtAuthFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        // El response no debe tener código de error (el chain.filter dejó pasar)
        assertThat(exchange.getResponse().getStatusCode()).isNull(); // null = no se sobreescribió
    }

    // ─── Caso 2: token de tipo refresh → debe rechazar con 401 ───────────────

    @Test
    void tokenRefresh_debeRechazarCon401() {
        String jwt = generarToken("refresh", 60_000);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        var gatewayFilter = filter.apply(new JwtAuthFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        // El filtro debe haber puesto 401 antes de dejar pasar
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─── Caso 3: sin header Authorization → 401 ──────────────────────────────

    @Test
    void sinHeaderAuthorization_debeRechazarCon401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/catalog")
                .build(); // sin header Authorization
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        var gatewayFilter = filter.apply(new JwtAuthFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─── Caso 4: header sin prefijo "Bearer " → 401 ──────────────────────────

    @Test
    void headerSinPrefixBearer_debeRechazarCon401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz") // Basic auth, no Bearer
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        var gatewayFilter = filter.apply(new JwtAuthFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─── Caso 5: token expirado → 401 ────────────────────────────────────────

    @Test
    void tokenExpirado_debeRechazarCon401() {
        // expirationMs negativo → ya expiró en el momento de crearlo
        String jwt = generarToken("access", -1_000);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        var gatewayFilter = filter.apply(new JwtAuthFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─── Caso 6: token con firma incorrecta → 401 ────────────────────────────

    @Test
    void tokenConFirmaIncorrecta_debeRechazarCon401() {
        // Generamos con una clave diferente — la validación de firma fallará
        Key otraKey = Keys.hmacShaKeyFor("otra-clave-completamente-diferente-32c".getBytes());
        String jwt = Jwts.builder()
                .setSubject("hacker")
                .claim("type", "access")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otraKey, SignatureAlgorithm.HS256)
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        var gatewayFilter = filter.apply(new JwtAuthFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─── Caso 7: token completamente malformado → 401 ────────────────────────

    @Test
    void tokenMalformado_debeRechazarCon401() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer esto.no.es.un.jwt.valido")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        var gatewayFilter = filter.apply(new JwtAuthFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
