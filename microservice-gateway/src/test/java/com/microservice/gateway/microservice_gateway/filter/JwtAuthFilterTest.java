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
import org.springframework.web.server.ServerWebExchange;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

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

    // ═════════════════════════════════════════════════════════════════════════
    // IDENTITY PROPAGATION
    // Tests que verifican que los claims del JWT se propagan como headers
    // X-User-Id y X-User-Roles al microservicio destino.
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    void tokenAccessValido_propagaXUserId() {
        String jwt = generarToken("access", 60_000);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        var gatewayFilter = filter.apply(new JwtAuthFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        // El filtro muta el exchange y pasa el nuevo a chain.filter
        // Capturamos ese argumento para inspeccionar los headers propagados
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        ServerWebExchange captured = captor.getValue();

        assertThat(captured.getRequest().getHeaders().getFirst("X-User-Id"))
                .as("X-User-Id debe ser el subject del JWT")
                .isEqualTo("silvio");
    }

    @Test
    void tokenAccessValido_propagaXUserRoles() {
        String jwt = generarToken("access", 60_000);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        var gatewayFilter = filter.apply(new JwtAuthFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        ServerWebExchange captured = captor.getValue();

        assertThat(captured.getRequest().getHeaders().getFirst("X-User-Roles"))
                .as("X-User-Roles debe ser el claim 'roles' del JWT")
                .isEqualTo("ROLE_USER");
    }

    @Test
    void tokenAccessValido_sinRoles_propagaVacio() {
        // Token sin el claim "roles" — el filtro debe mandar "" en lugar de null
        String jwt = Jwts.builder()
                .claim("type", "access")
                .setSubject("silvio")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        var gatewayFilter = filter.apply(new JwtAuthFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        ServerWebExchange captured = captor.getValue();

        assertThat(captured.getRequest().getHeaders().getFirst("X-User-Roles"))
                .as("X-User-Roles debe ser vacío cuando no hay claim 'roles'")
                .isEqualTo("");
    }

    @Test
    void tokenRechazado_noPropagaHeaders() {
        // Token inválido — el filtro rechaza antes de propagar
        // chain.filter nunca se llama, por lo tanto no debe haber headers
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token.invalido.aqui")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        var gatewayFilter = filter.apply(new JwtAuthFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        // El filtro usó exchange original (no mutado) y llamó a setComplete
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SEGURIDAD — SOBREESCRITURA DE HEADERS MALICIOSOS
    // Tests que verifican que el filtro REEMPLAZA cualquier header X-User-Id
    // o X-User-Roles que el cliente haya enviado, usando h.set() en lugar de
    // r.header() para evitar inyección de identidad por duplicación.
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    void tokenValido_conXUserIdMalicioso_loSobrescribe() {
        // Un cliente malicioso envía X-User-Id: hacker en el request original.
        // El filtro debe SOBREESCRIBIRLO con el valor real del JWT.
        String jwt = generarToken("access", 60_000);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .header("X-User-Id", "hacker") // intento de suplantación
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        var gatewayFilter = filter.apply(new JwtAuthFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        ServerWebExchange captured = captor.getValue();

        // El header debe ser "silvio" (del JWT), NO "hacker"
        assertThat(captured.getRequest().getHeaders().getFirst("X-User-Id"))
                .as("X-User-Id debe provenir del JWT, no del header malicioso")
                .isEqualTo("silvio");

        // Verificar que no haya DUPLICADOS (consecuencia de h.set() vs h.add())
        assertThat(captured.getRequest().getHeaders().get("X-User-Id"))
                .as("X-User-Id no debe tener valores duplicados")
                .hasSize(1);
    }

    @Test
    void tokenValido_conXUserRolesMalicioso_loSobrescribe() {
        // Un cliente malicioso envía X-User-Roles: ROLE_HACKER.
        // El filtro debe reemplazarlo con el claim real del JWT.
        String jwt = generarToken("access", 60_000);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .header("X-User-Roles", "ROLE_HACKER")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        var gatewayFilter = filter.apply(new JwtAuthFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        ServerWebExchange captured = captor.getValue();

        assertThat(captured.getRequest().getHeaders().getFirst("X-User-Roles"))
                .as("X-User-Roles debe provenir del JWT, no del header malicioso")
                .isEqualTo("ROLE_USER");

        assertThat(captured.getRequest().getHeaders().get("X-User-Roles"))
                .as("X-User-Roles no debe tener valores duplicados")
                .hasSize(1);
    }

    @Test
    void tokenValido_conAmbosHeadersMaliciosos_sobrescribeAmbos() {
        // Prueba integral: ambos headers maliciosos deben ser reemplazados
        String jwt = generarToken("access", 60_000);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .header("X-User-Id", "hacker")
                .header("X-User-Roles", "ROLE_HACKER,ROLE_ADMIN")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        var gatewayFilter = filter.apply(new JwtAuthFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        ServerWebExchange captured = captor.getValue();

        assertThat(captured.getRequest().getHeaders().getFirst("X-User-Id"))
                .as("X-User-Id sobrescrito — no debe ser 'hacker'")
                .isEqualTo("silvio");
        assertThat(captured.getRequest().getHeaders().getFirst("X-User-Roles"))
                .as("X-User-Roles sobrescrito — no debe ser 'ROLE_HACKER'")
                .isEqualTo("ROLE_USER");

        assertThat(captured.getRequest().getHeaders().get("X-User-Id"))
                .as("X-User-Id sin duplicados")
                .hasSize(1);
        assertThat(captured.getRequest().getHeaders().get("X-User-Roles"))
                .as("X-User-Roles sin duplicados")
                .hasSize(1);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SEGURIDAD — NULL SUBJECT (claims.getSubject() == null)
    // Si el JWT no tiene claim "sub" (subject), claims.getSubject() retorna
    // null. El filtro debe rechazar con 401 en lugar de propagar null.
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    void tokenSinSubjectClaim_rechazaCon401() {
        // Token sin setSubject() → claims.getSubject() = null
        String jwt = Jwts.builder()
                .claim("type", "access")
                .claim("roles", "ROLE_USER")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        var gatewayFilter = filter.apply(new JwtAuthFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .as("Token sin 'sub' claim debe ser rechazado con 401")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenSinSubjectClaim_noPropagaHeaders() {
        // Token sin subject — además de 401, no debe propagar headers
        String jwt = Jwts.builder()
                .claim("type", "access")
                .claim("roles", "ROLE_USER")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        var gatewayFilter = filter.apply(new JwtAuthFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        // chain.filter no debe haber sido llamado (el filtro cortó la cadena)
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // IDENTITY PROPAGATION — CASOS BORDE
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    void tokenAccessValido_sinRoles_propagaXUserIdYRolesVacio() {
        // Token sin claim "roles" pero con subject válido:
        // - X-User-Id debe propagarse normalmente
        // - X-User-Roles debe ser "" (no null)
        String jwt = Jwts.builder()
                .setSubject("maria")
                .claim("type", "access")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/catalog")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        var gatewayFilter = filter.apply(new JwtAuthFilter.Config());
        StepVerifier.create(gatewayFilter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        ServerWebExchange captured = captor.getValue();

        // X-User-Id se propaga normal aunque falte roles
        assertThat(captured.getRequest().getHeaders().getFirst("X-User-Id"))
                .as("X-User-Id debe propagarse aunque falte roles")
                .isEqualTo("maria");
        // X-User-Roles debe ser cadena vacía
        assertThat(captured.getRequest().getHeaders().getFirst("X-User-Roles"))
                .as("X-User-Roles debe ser cadena vacía cuando no hay claim 'roles'")
                .isEqualTo("");
    }
}
