package com.microservice.gateway.microservice_gateway.filter;

import com.microservice.gateway.microservice_gateway.config.RateLimitingConfig;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

// ¿QUÉ HACE ESTE FILTRO?
//
// Es un filtro GLOBAL que se ejecuta ANTES que cualquier otro filtro
// (incluyendo JwtAuthFilter). Su trabajo es proteger los 13 microservicios
// de saturación usando el algoritmo Token Bucket (Bucket4j).
//
// Tiene DOS niveles de protección:
//
//   1. Límite GLOBAL: controla el total de requests que entran al gateway
//      (ej: 1000 requests/minuto). Protege contra picos masivos.
//
//   2. Límite POR IP: cada cliente tiene su propio bucket
//      (ej: 50 requests/minuto). Un cliente agresivo no afecta a los demás.
//
// Si se excede cualquiera de los dos límites → 429 Too Many Requests
//
// ¿POR QUÉ GLOBAL Y NO POR RUTA?
// Porque queremos proteger la capacidad total del gateway antes de que
// las requests se distribuyan a los 13 servicios. Si el gateway se satura,
// no importa a qué servicio vaya la request — todas fallan igual.
//
// ¿POR QUÉ @Order(-1)?
// Los GlobalFilter con orden negativo se ejecutan antes que los que tienen
// orden 0 o superior. JwtAuthFilter no implementa Ordered, así que usa
// el valor por defecto (0). Con @Order(-1) garantizamos que el rate
// limiter se ejecute SIEMPRE primero.

@Component
@Order(-1)
public class GlobalRateLimitingFilter implements GlobalFilter, Ordered {

    private final Bucket globalBucket;
    private final Map<String, Bucket> ipBuckets;
    private final RateLimitingConfig config;

    public GlobalRateLimitingFilter(Bucket globalBucket,
                                     Map<String, Bucket> ipBuckets,
                                     RateLimitingConfig config) {
        this.globalBucket = globalBucket;
        this.ipBuckets = ipBuckets;
        this.config = config;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // ─── Paso 1: Obtener IP real del cliente ───
        // Soporta proxies mediante el header X-Forwarded-For
        String clientIp = getClientIp(exchange);

        // ─── Paso 2: Verificar límite GLOBAL ───
        // Si el gateway ya recibió demasiadas requests, cortamos aquí
        // sin importar qué IP las envía ni a qué servicio van
        if (!globalBucket.tryConsume(1)) {
            return rechazarCon429(exchange);
        }

        // ─── Paso 3: Verificar límite POR IP ───
        // Cada IP tiene su propio bucket. Si no existe aún, se crea
        // automáticamente con los límites configurados
        Bucket ipBucket = ipBuckets.computeIfAbsent(clientIp, this::crearBucketPorIp);
        if (!ipBucket.tryConsume(1)) {
            return rechazarCon429(exchange);
        }

        // ─── Paso 4: Todo ok ───
        // Dejar pasar al siguiente filtro (JwtAuthFilter) o directamente
        // al microservicio destino si no hay más filtros
        return chain.filter(exchange);
    }

    /**
     * Crea un nuevo bucket para una IP que aún no tiene uno.
     * Los límites se leen de rate-limiting.per-ip.* en la configuración.
     */
    private Bucket crearBucketPorIp(String ip) {
        return Bucket4j.builder()
                .addLimit(Bandwidth.classic(
                        config.getPerIpCapacity(),
                        Refill.greedy(config.getPerIpRefillPerMinute(), Duration.ofMinutes(1))
                ))
                .build();
    }

    /**
     * Extrae la IP real del cliente desde la request.
     *
     * Si la request pasó por un proxy o balanceador, el header
     * X-Forwarded-For contiene la IP original del cliente.
     * Si no hay proxy, usamos la dirección remota directa.
     */
    private String getClientIp(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest()
                .getHeaders()
                .getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }

    /**
     * Rechaza la request con HTTP 429 Too Many Requests.
     * Incluye el header Retry-After: 60 para que el cliente sepa
     * cuándo puede reintentar (estándar HTTP).
     */
    private Mono<Void> rechazarCon429(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().add("Retry-After", "60");
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -1; // Coincide con @Order(-1) por redundancia y claridad
    }
}
