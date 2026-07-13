package com.microservice.gateway.microservice_gateway.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuración del rate limiter basado en Bucket4j (en memoria).
 *
 * ¿POR QUÉ BUCKET4J?
 * Bucket4j implementa el algoritmo Token Bucket, que es el estándar
 * para rate limiting. Almacena los buckets en memoria (ConcurrentHashMap)
 * sin necesidad de Redis ni infraestructura externa.
 *
 * ¿CÓMO FUNCIONA?
 * - Cada request consume un token del bucket global y otro del bucket por IP
 * - Si no hay tokens disponibles → 429 Too Many Requests
 * - Los tokens se regeneran automáticamente cada minuto (Refill)
 *
 * Los valores se configuran en msvc-gateway.yml (Config Server)
 * con defaults razonables si no están presentes.
 *
 * @see com.microservice.gateway.microservice_gateway.filter.GlobalRateLimitingFilter
 */
@Configuration
public class RateLimitingConfig {

    // ──────────────────────────────────────────────
    // Límite GLOBAL: aplica a TODAS las requests del gateway
    // ──────────────────────────────────────────────

    @Value("${rate-limiting.global.capacity:1000}")
    private int globalCapacity;

    @Value("${rate-limiting.global.refill-per-minute:1000}")
    private int globalRefillPerMinute;

    // ──────────────────────────────────────────────
    // Límite POR IP: cada cliente tiene su propio bucket
    // ──────────────────────────────────────────────

    @Value("${rate-limiting.per-ip.capacity:50}")
    private int perIpCapacity;

    @Value("${rate-limiting.per-ip.refill-per-minute:50}")
    private int perIpRefillPerMinute;

    /**
     * Bucket global compartido por todas las requests entrantes.
     * Ejemplo con valores por defecto: 1000 requests/minuto en total.
     */
    @Bean
    public Bucket globalBucket() {
        return Bucket4j.builder()
                .addLimit(Bandwidth.classic(
                        globalCapacity,
                        Refill.greedy(globalRefillPerMinute, Duration.ofMinutes(1))
                ))
                .build();
    }

    /**
     * Mapa concurrente de buckets por IP de cliente.
     * Cada IP tiene su propio límite individual (ej: 50 requests/minuto).
     * ConcurrentHashMap es thread-safe y no necesita Redis — los buckets
     * se crean bajo demanda cuando una IP nueva hace su primer request.
     */
    @Bean
    public Map<String, Bucket> ipBuckets() {
        return new ConcurrentHashMap<>();
    }

    // ──────────────────────────────────────────────
    // Getters para que el filtro cree buckets dinámicamente
    // ──────────────────────────────────────────────

    public int getPerIpCapacity() {
        return perIpCapacity;
    }

    public int getPerIpRefillPerMinute() {
        return perIpRefillPerMinute;
    }
}
