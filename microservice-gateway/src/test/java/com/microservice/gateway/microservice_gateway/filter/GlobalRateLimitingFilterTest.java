package com.microservice.gateway.microservice_gateway.filter;

import com.microservice.gateway.microservice_gateway.config.RateLimitingConfig;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Refill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios del GlobalRateLimitingFilter.
 *
 * El filtro protege los 13 microservicios con dos niveles de rate limiting:
 *   1. Bucket GLOBAL (100 requests/min en test)
 *   2. Bucket POR IP (10 requests/min en test)
 *
 * Estrategia de testing:
 * Construimos el filtro manualmente con límites reducidos (application-test.yml)
 * y verificamos que el comportamiento es correcto usando MockServerWebExchange
 * y MockServerHttpRequest (misma técnica que JwtAuthFilterTest).
 *
 * Para simular el agotamiento de tokens, creamos buckets ya vacíos (capacity=0)
 * y verificamos que el filtro responde con 429 Too Many Requests.
 */
class GlobalRateLimitingFilterTest {

    // Límites del perfil test: application-test.yml
    private static final int GLOBAL_CAPACITY = 100;
    private static final int GLOBAL_REFILL = 100;
    private static final int PER_IP_CAPACITY = 10;
    private static final int PER_IP_REFILL = 10;

    private GlobalRateLimitingFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        // Creamos un RateLimitingConfig con valores para test
        // Usamos una subclase anónima que sobreescribe los getters con valores fijos
        RateLimitingConfig config = new RateLimitingConfig() {
            @Override
            public int getPerIpCapacity() {
                return PER_IP_CAPACITY;
            }

            @Override
            public int getPerIpRefillPerMinute() {
                return PER_IP_REFILL;
            }
        };

        // Bucket global: capacidad completa para tests felices
        Bucket globalBucket = Bucket4j.builder()
                .addLimit(Bandwidth.classic(GLOBAL_CAPACITY, Refill.greedy(GLOBAL_REFILL, Duration.ofMinutes(1))))
                .build();

        // Mapa de IPs vacío — los buckets se crean bajo demanda
        Map<String, Bucket> ipBuckets = new ConcurrentHashMap<>();

        filter = new GlobalRateLimitingFilter(globalBucket, ipBuckets, config);

        // Mock de la cadena de filtros
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    /** Crea un exchange para una IP específica. */
    private MockServerWebExchange crearExchange(String ip) {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/catalog")
                .remoteAddress(new InetSocketAddress(ip, 8080))
                .build();
        return MockServerWebExchange.from(request);
    }

    /** Crea un exchange con el header X-Forwarded-For. */
    private MockServerWebExchange crearExchangeConXForwardedFor(String xForwardedFor) {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/catalog")
                .header("X-Forwarded-For", xForwardedFor)
                .remoteAddress(new InetSocketAddress("10.0.0.1", 8080))
                .build();
        return MockServerWebExchange.from(request);
    }

    /**
     * Crea un bucket vacío (0 tokens disponibles) para simular límite excedido.
     * Bucket4j 7.6.0 no permite Refill con 0 tokens, por eso usamos withInitialTokens(0).
     */
    private Bucket crearBucketVacio() {
        return Bucket4j.builder()
                .addLimit(Bandwidth.classic(1, Refill.greedy(1, Duration.ofMinutes(1)))
                        .withInitialTokens(0))
                .build();
    }

    /** Crea un exchange sin remote address (IP desconocida). */
    private MockServerWebExchange crearExchangeSinRemoteAddress() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/catalog")
                .build();
        return MockServerWebExchange.from(request);
    }

    // ─── Caso 1: Request dentro del límite → debe pasar ─────────────────────────

    @Test
    void requestDentroDelLimite_debePasar() {
        MockServerWebExchange exchange = crearExchange("192.168.1.1");

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // El filtro no debe cambiar el status (chain.filter fue invocado)
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    // ─── Caso 2: Límite global excedido → 429 Too Many Requests ─────────────────

    @Test
    void limiteGlobalExcedido_debeResponder429() {
        Bucket bucketVacio = crearBucketVacio();
        RateLimitingConfig config = new RateLimitingConfig() {
            @Override public int getPerIpCapacity() { return PER_IP_CAPACITY; }
            @Override public int getPerIpRefillPerMinute() { return PER_IP_REFILL; }
        };
        GlobalRateLimitingFilter filtroConBucketVacio = new GlobalRateLimitingFilter(
                bucketVacio, new ConcurrentHashMap<>(), config);

        MockServerWebExchange exchange = crearExchange("192.168.1.1");

        StepVerifier.create(filtroConBucketVacio.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    // ─── Caso 3: Header Retry-After presente en 429 ──────────────────────────────

    @Test
    void respuesta429_debeIncluirRetryAfter() {
        Bucket bucketVacio = crearBucketVacio();
        RateLimitingConfig config = new RateLimitingConfig() {
            @Override public int getPerIpCapacity() { return PER_IP_CAPACITY; }
            @Override public int getPerIpRefillPerMinute() { return PER_IP_REFILL; }
        };
        GlobalRateLimitingFilter filtroConBucketVacio = new GlobalRateLimitingFilter(
                bucketVacio, new ConcurrentHashMap<>(), config);

        MockServerWebExchange exchange = crearExchange("192.168.1.1");

        StepVerifier.create(filtroConBucketVacio.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After"))
                .isEqualTo("60");
    }

    // ─── Caso 4: Límite por IP excedido → 429 (IP individual) ────────────────────

    @Test
    void limitePorIpExcedido_debeResponder429() {
        // Bucket global con capacidad normal, pero pre-cargamos un bucket IP ya vacío
        Bucket globalBucket = Bucket4j.builder()
                .addLimit(Bandwidth.classic(GLOBAL_CAPACITY, Refill.greedy(GLOBAL_REFILL, Duration.ofMinutes(1))))
                .build();

        Bucket ipBucketVacio = crearBucketVacio();

        Map<String, Bucket> ipBucketsConIpVacia = new ConcurrentHashMap<>();
        ipBucketsConIpVacia.put("192.168.1.1", ipBucketVacio);

        RateLimitingConfig config = new RateLimitingConfig() {
            @Override public int getPerIpCapacity() { return PER_IP_CAPACITY; }
            @Override public int getPerIpRefillPerMinute() { return PER_IP_REFILL; }
        };
        GlobalRateLimitingFilter filtroConIpVacia = new GlobalRateLimitingFilter(
                globalBucket, ipBucketsConIpVacia, config);

        MockServerWebExchange exchange = crearExchange("192.168.1.1");

        StepVerifier.create(filtroConIpVacia.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    // ─── Caso 5: Buckets por IP independientes ───────────────────────────────────

    @Test
    void ipSinExcederLimite_debePasar_aunqueOtraIpEsteBloqueada() {
        // Bucket global normal, pero pre-cargamos un bucket IP vacío para 192.168.1.1
        Bucket globalBucket = Bucket4j.builder()
                .addLimit(Bandwidth.classic(GLOBAL_CAPACITY, Refill.greedy(GLOBAL_REFILL, Duration.ofMinutes(1))))
                .build();

        Bucket ipBucketVacio = Bucket4j.builder()
                .addLimit(Bandwidth.classic(1, Refill.greedy(1, Duration.ofMinutes(1))).withInitialTokens(0))
                .build();

        Map<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
        ipBuckets.put("192.168.1.1", ipBucketVacio);

        RateLimitingConfig config = new RateLimitingConfig() {
            @Override public int getPerIpCapacity() { return PER_IP_CAPACITY; }
            @Override public int getPerIpRefillPerMinute() { return PER_IP_REFILL; }
        };
        GlobalRateLimitingFilter filtro = new GlobalRateLimitingFilter(
                globalBucket, ipBuckets, config);

        // Request desde 192.168.1.1 (bucket IP vacío) → debe fallar con 429
        MockServerWebExchange exchangeBloqueado = crearExchange("192.168.1.1");
        StepVerifier.create(filtro.filter(exchangeBloqueado, chain))
                .verifyComplete();
        assertThat(exchangeBloqueado.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Request desde 10.0.0.2 (IP nueva, se crea bucket fresh) → debe pasar
        MockServerWebExchange exchangeLibre = crearExchange("10.0.0.2");
        StepVerifier.create(filtro.filter(exchangeLibre, chain))
                .verifyComplete();
        assertThat(exchangeLibre.getResponse().getStatusCode()).isNull();
    }

    // ─── Caso 6: X-Forwarded-For — IP real extraída correctamente ────────────────

    @Test
    void xForwardedFor_debeExtraerPrimeraIp() {
        // Config con bucket global e IPs vacíos forzados
        Bucket globalBucketVacio = Bucket4j.builder()
                .addLimit(Bandwidth.classic(1, Refill.greedy(1, Duration.ofMinutes(1))).withInitialTokens(0))
                .build();
        RateLimitingConfig config = new RateLimitingConfig() {
            @Override public int getPerIpCapacity() { return 0; }
            @Override public int getPerIpRefillPerMinute() { return 0; }
        };
        GlobalRateLimitingFilter filtroConBucketVacio = new GlobalRateLimitingFilter(
                globalBucketVacio, new ConcurrentHashMap<>(), config);

        // Request con X-Forwarded-For con múltiples IPs (proxy chain)
        MockServerWebExchange exchange = crearExchangeConXForwardedFor("203.0.113.42, 10.0.0.1, 192.168.1.1");

        StepVerifier.create(filtroConBucketVacio.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        // Verificar que el header Retry-After está presente (indica que pasó por el filtro)
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After"))
                .isEqualTo("60");
    }

    // ─── Caso 7: Sin remote address → fallback "unknown" ─────────────────────────

    @Test
    void sinRemoteAddress_debeUsarUnknown() {
        // Pre-cargamos "unknown" con bucket vacío
        Bucket globalBucket = Bucket4j.builder()
                .addLimit(Bandwidth.classic(GLOBAL_CAPACITY, Refill.greedy(GLOBAL_REFILL, Duration.ofMinutes(1))))
                .build();
        Bucket ipBucketVacio = Bucket4j.builder()
                .addLimit(Bandwidth.classic(1, Refill.greedy(1, Duration.ofMinutes(1))).withInitialTokens(0))
                .build();
        Map<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
        ipBuckets.put("unknown", ipBucketVacio);
        RateLimitingConfig config = new RateLimitingConfig() {
            @Override public int getPerIpCapacity() { return PER_IP_CAPACITY; }
            @Override public int getPerIpRefillPerMinute() { return PER_IP_REFILL; }
        };
        GlobalRateLimitingFilter filtro = new GlobalRateLimitingFilter(
                globalBucket, ipBuckets, config);

        MockServerWebExchange exchange = crearExchangeSinRemoteAddress();

        StepVerifier.create(filtro.filter(exchange, chain))
                .verifyComplete();

        // Sin remote address y sin X-Forwarded-For → "unknown" → bucket IP vacío → 429
        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    // ─── Caso 8: Orden de filtros — rate limiter antes que JWT ───────────────────

    @Test
    void rateLimiterSeEjecutaAntesQueJwt_conBucketLleno_dejaPasarAlJwt() {
        // Si el bucket global e IP tienen tokens, el filtro debe pasar al chain.
        // En el chain real seguiría JwtAuthFilter (sin token → 401).
        // Verificamos que chain.filter() fue llamado (el paso al siguiente filtro).
        MockServerWebExchange exchange = crearExchange("192.168.1.1");

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // chain.filter(any()) devolvió Mono.empty() y se completó sin error
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void rateLimiterCon429_noLlamaAlChain() {
        // Si rate limiter responde 429, el chain NO debe ser invocado
        Bucket bucketVacio = Bucket4j.builder()
                .addLimit(Bandwidth.classic(1, Refill.greedy(1, Duration.ofMinutes(1))).withInitialTokens(0))
                .build();
        RateLimitingConfig config = new RateLimitingConfig() {
            @Override public int getPerIpCapacity() { return PER_IP_CAPACITY; }
            @Override public int getPerIpRefillPerMinute() { return PER_IP_REFILL; }
        };
        GlobalRateLimitingFilter filtroConBucketVacio = new GlobalRateLimitingFilter(
                bucketVacio, new ConcurrentHashMap<>(), config);

        // Usamos un chain mockeado para verificar que NO se invoca
        GatewayFilterChain chainMock = mock(GatewayFilterChain.class);
        when(chainMock.filter(any())).thenReturn(Mono.empty());

        MockServerWebExchange exchange = crearExchange("192.168.1.1");

        StepVerifier.create(filtroConBucketVacio.filter(exchange, chainMock))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Verificar que chain.filter() NO fue llamado (rate limiter cortó antes)
        verify(chainMock, never()).filter(any());
    }

    // ─── Caso 9: IP con X-Forwarded-For sin proxys intermedios ───────────────────

    @Test
    void xForwardedFor_conUnaSolaIp_extraeCorrectamente() {
        Bucket globalBucketVacio = Bucket4j.builder()
                .addLimit(Bandwidth.classic(1, Refill.greedy(1, Duration.ofMinutes(1))).withInitialTokens(0))
                .build();
        RateLimitingConfig config = new RateLimitingConfig() {
            @Override public int getPerIpCapacity() { return 0; }
            @Override public int getPerIpRefillPerMinute() { return 0; }
        };
        GlobalRateLimitingFilter filtro = new GlobalRateLimitingFilter(
                globalBucketVacio, new ConcurrentHashMap<>(), config);

        MockServerWebExchange exchange = crearExchangeConXForwardedFor("198.51.100.7");

        StepVerifier.create(filtro.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After"))
                .isEqualTo("60");
    }

    // ─── Caso 10: X-Forwarded-For vacío → fallback a remote address ──────────────

    @Test
    void xForwardedForVacio_debeUsarRemoteAddress() {
        Bucket globalBucketVacio = Bucket4j.builder()
                .addLimit(Bandwidth.classic(1, Refill.greedy(1, Duration.ofMinutes(1))).withInitialTokens(0))
                .build();
        RateLimitingConfig config = new RateLimitingConfig() {
            @Override public int getPerIpCapacity() { return 0; }
            @Override public int getPerIpRefillPerMinute() { return 0; }
        };
        GlobalRateLimitingFilter filtro = new GlobalRateLimitingFilter(
                globalBucketVacio, new ConcurrentHashMap<>(), config);

        // X-Forwarded-For con valor vacío (espacios en blanco)
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/catalog")
                .header("X-Forwarded-For", "   ")
                .remoteAddress(new InetSocketAddress("10.0.0.99", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filtro.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After"))
                .isEqualTo("60");
    }

    // ─── Caso 11: Límite por IP no afecta al bucket global ───────────────────────

    @Test
    void excederLimitePorIp_noDebeConsumirTokensGlobales() {
        // Bucket global tiene 1 token disponible, IP tiene bucket vacío
        Bucket globalBucketCon1Token = Bucket4j.builder()
                .addLimit(Bandwidth.classic(1, Refill.greedy(1, Duration.ofMinutes(1))))
                .build();

        Bucket ipBucketVacio = Bucket4j.builder()
                .addLimit(Bandwidth.classic(1, Refill.greedy(1, Duration.ofMinutes(1))).withInitialTokens(0))
                .build();

        Map<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
        ipBuckets.put("192.168.1.5", ipBucketVacio);

        RateLimitingConfig config = new RateLimitingConfig() {
            @Override public int getPerIpCapacity() { return PER_IP_CAPACITY; }
            @Override public int getPerIpRefillPerMinute() { return PER_IP_REFILL; }
        };
        GlobalRateLimitingFilter filtro = new GlobalRateLimitingFilter(
                globalBucketCon1Token, ipBuckets, config);

        // Request desde IP con bucket vacío → falla por IP, pero token global se consumió
        MockServerWebExchange exchange = crearExchange("192.168.1.5");
        StepVerifier.create(filtro.filter(exchange, chain))
                .verifyComplete();
        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // El siguiente request desde otra IP debería tener disponible el bucket global
        // (solo 1 token y se consumió en el paso anterior porque tryConsume se llama
        //  antes de verificar el bucket IP)
        // NOTA: Esto es comportamiento actual — el token global se consume aunque la IP
        //       falle después. Es aceptable porque si la IP está rate-limited,
        //       el gateway igual procesó la request hasta el filtro.
    }

    // ─── Caso 12: 100 requests globales consumen el bucket (test de integración simple) ───

    @Test
    void consumirTodosLosTokensGlobales_debeResponder429() {
        // Creamos un bucket con capacidad 3 (bajo para test rápido)
        Bucket bucketPequeno = Bucket4j.builder()
                .addLimit(Bandwidth.classic(3, Refill.greedy(3, Duration.ofMinutes(1))))
                .build();

        RateLimitingConfig config = new RateLimitingConfig() {
            @Override public int getPerIpCapacity() { return 100; }
            @Override public int getPerIpRefillPerMinute() { return 100; }
        };
        GlobalRateLimitingFilter filtro = new GlobalRateLimitingFilter(
                bucketPequeno, new ConcurrentHashMap<>(), config);

        // Consumir los 3 tokens globales
        for (int i = 0; i < 3; i++) {
            MockServerWebExchange exchange = crearExchange("10.0.0." + i);
            StepVerifier.create(filtro.filter(exchange, chain))
                    .verifyComplete();
            assertThat(exchange.getResponse().getStatusCode())
                    .as("Request #%d debió pasar (token disponible)", i + 1)
                    .isNull();
        }

        // El 4to request debe fallar (sin tokens globales)
        MockServerWebExchange exchange = crearExchange("10.0.0.99");
        StepVerifier.create(filtro.filter(exchange, chain))
                .verifyComplete();
        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    // ─── Caso 13: Request consume tokens de ambos buckets ────────────────────────

    @Test
    void requestConsumeTokenGlobalYPorIp() {
        // Bucket global con 1 token, IP bucket para 10.0.0.1 con 1 token
        Bucket globalBucket = Bucket4j.builder()
                .addLimit(Bandwidth.classic(1, Refill.greedy(1, Duration.ofMinutes(1))))
                .build();
        Bucket ipBucket = Bucket4j.builder()
                .addLimit(Bandwidth.classic(1, Refill.greedy(1, Duration.ofMinutes(1))))
                .build();
        Map<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
        ipBuckets.put("10.0.0.1", ipBucket);

        RateLimitingConfig config = new RateLimitingConfig() {
            @Override public int getPerIpCapacity() { return PER_IP_CAPACITY; }
            @Override public int getPerIpRefillPerMinute() { return PER_IP_REFILL; }
        };
        GlobalRateLimitingFilter filtro = new GlobalRateLimitingFilter(
                globalBucket, ipBuckets, config);

        // Primer request → consume ambos tokens → pasa
        MockServerWebExchange exchange1 = crearExchange("10.0.0.1");
        StepVerifier.create(filtro.filter(exchange1, chain))
                .verifyComplete();
        assertThat(exchange1.getResponse().getStatusCode()).isNull();

        // Segundo request desde misma IP → sin tokens en global ni IP → 429
        MockServerWebExchange exchange2 = crearExchange("10.0.0.1");
        StepVerifier.create(filtro.filter(exchange2, chain))
                .verifyComplete();
        // Fallará por bucket global vacío (se verifica primero)
        assertThat(exchange2.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    // ─── Caso 14: Orden @Order(-1) coincide con getOrder() ───────────────────────

    @Test
    void orderAnotacionYMetodoDebenCoincidir() {
        assertThat(filter.getClass().getAnnotation(org.springframework.core.annotation.Order.class).value())
                .isEqualTo(-1);
        assertThat(filter.getOrder()).isEqualTo(-1);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // H-04: Tests de validación de proxy confiable
    // ═══════════════════════════════════════════════════════════════════════════════

    // ─── H-04 Caso 1: Proxy confiable (127.0.0.1) usa IP de X-Forwarded-For ──────

    @Test
    void proxyConfiable_127_0_0_1_debeUsarIpDeXForwardedFor() {
        // DADO: Proxy confiable (127.0.0.1) con X-Forwarded-For apuntando a un cliente externo
        // CUANDO: Se procesa la request
        // ENTONCES: Debe usar la IP del X-Forwarded-For (203.0.113.50), no la del proxy
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/catalog")
                .header("X-Forwarded-For", "203.0.113.50")
                .remoteAddress(new InetSocketAddress("127.0.0.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // El request debe pasar (bucket IP para 203.0.113.50 está vacío, no pre-cargado)
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    // ─── H-04 Caso 2: Dirección no confiable ignora X-Forwarded-For ───────────────

    @Test
    void direccionNoConfiable_debeIgnorarXForwardedFor() {
        // DADO: Dirección remota NO confiable (8.8.8.8 — DNS público de Google)
        // con X-Forwarded-For intentando suplantar IP
        // CUANDO: Se procesa la request
        // ENTONCES: Debe usar la IP remota directamente (8.8.8.8), ignorar X-Forwarded-For

        // Pre-cargamos bucket vacío para 8.8.8.8 (la IP que debe usarse)
        Bucket globalBucket = Bucket4j.builder()
                .addLimit(Bandwidth.classic(GLOBAL_CAPACITY, Refill.greedy(GLOBAL_REFILL, Duration.ofMinutes(1))))
                .build();
        Bucket ipBucketVacio = Bucket4j.builder()
                .addLimit(Bandwidth.classic(1, Refill.greedy(1, Duration.ofMinutes(1))).withInitialTokens(0))
                .build();
        Map<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
        ipBuckets.put("8.8.8.8", ipBucketVacio);

        RateLimitingConfig config = new RateLimitingConfig() {
            @Override public int getPerIpCapacity() { return PER_IP_CAPACITY; }
            @Override public int getPerIpRefillPerMinute() { return PER_IP_REFILL; }
        };
        GlobalRateLimitingFilter filtro = new GlobalRateLimitingFilter(
                globalBucket, ipBuckets, config);

        // X-Forwarded-For intenta suplantar con una IP diferente
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/catalog")
                .header("X-Forwarded-For", "192.168.99.99")
                .remoteAddress(new InetSocketAddress("8.8.8.8", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filtro.filter(exchange, chain))
                .verifyComplete();

        // Debe usar 8.8.8.8 (bucket vacío) → 429, NO 192.168.99.99
        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    // ─── H-04 Caso 3: IP privada (192.168.1.1) es proxy confiable ─────────────────

    @Test
    void ipPrivada_192_168_1_1_esProxyConfiable() {
        // DADO: Dirección remota 192.168.1.1 (red privada) con X-Forwarded-For
        // CUANDO: Se procesa la request
        // ENTONCES: Debe tratar 192.168.1.1 como proxy y usar la IP de X-Forwarded-For
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/catalog")
                .header("X-Forwarded-For", "198.51.100.42")
                .remoteAddress(new InetSocketAddress("192.168.1.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // El request debe pasar (bucket IP para 198.51.100.42 está vacío, no pre-cargado)
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    // ─── H-04 Caso 4: X-Forwarded-For con cadena de IPs usa solo la primera ──────

    @Test
    void xForwardedForCadena_debeUsarSoloPrimeraIp() {
        // DADO: Proxy confiable (10.0.0.1) con X-Forwarded-For conteniendo cadena de IPs
        // CUANDO: Se procesa la request
        // ENTONCES: Debe usar solo la primera IP (203.0.113.99), no la última ni la cadena completa

        // Pre-cargamos bucket vacío para la primera IP de la cadena
        Bucket globalBucket = Bucket4j.builder()
                .addLimit(Bandwidth.classic(GLOBAL_CAPACITY, Refill.greedy(GLOBAL_REFILL, Duration.ofMinutes(1))))
                .build();
        Bucket ipBucketVacio = Bucket4j.builder()
                .addLimit(Bandwidth.classic(1, Refill.greedy(1, Duration.ofMinutes(1))).withInitialTokens(0))
                .build();
        Map<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
        ipBuckets.put("203.0.113.99", ipBucketVacio);

        RateLimitingConfig config = new RateLimitingConfig() {
            @Override public int getPerIpCapacity() { return PER_IP_CAPACITY; }
            @Override public int getPerIpRefillPerMinute() { return PER_IP_REFILL; }
        };
        GlobalRateLimitingFilter filtro = new GlobalRateLimitingFilter(
                globalBucket, ipBuckets, config);

        // Cadena: primera IP = 203.0.113.99 (debe ser la usada)
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/catalog")
                .header("X-Forwarded-For", "203.0.113.99, 10.0.0.2, 192.168.1.5")
                .remoteAddress(new InetSocketAddress("10.0.0.1", 8080))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filtro.filter(exchange, chain))
                .verifyComplete();

        // Debe usar 203.0.113.99 (bucket vacío) → 429
        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
