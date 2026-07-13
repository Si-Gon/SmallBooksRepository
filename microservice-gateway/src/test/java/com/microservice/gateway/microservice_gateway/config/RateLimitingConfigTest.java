package com.microservice.gateway.microservice_gateway.config;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de integración de RateLimitingConfig.
 *
 * Verifica que los beans de Bucket4j se crean correctamente con el
 * perfil "test" que define límites reducidos (100 global, 10 por IP).
 *
 * Usa @SpringBootTest para levantar el contexto real de Spring
 * (misma técnica que MicroserviceGatewayApplicationTests).
 *
 * NOTA sobre la inyección de ipBuckets:
 * Spring trata @Autowired Map<String, Bucket> como una colección de todos
 * los beans de tipo Bucket, NO como el bean específico ipBuckets().
 * Por eso usamos @Resource(name = "ipBuckets") para obtener el bean exacto
 * que definimos en RateLimitingConfig.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class RateLimitingConfigTest {

    @Autowired
    private Bucket globalBucket;

    @Resource(name = "ipBuckets")
    private Map<String, Bucket> ipBuckets;

    @Autowired
    private RateLimitingConfig config;

    @Test
    void globalBucket_debeSerUnBean() {
        assertThat(globalBucket).isNotNull();
    }

    @Test
    void ipBuckets_debeSerUnConcurrentHashMap() {
        assertThat(ipBuckets).isNotNull();
        assertThat(ipBuckets).isInstanceOf(ConcurrentHashMap.class);
    }

    @Test
    void ipBuckets_debeEstarVacio() {
        assertThat(ipBuckets).isEmpty();
    }

    @Test
    void globalBucket_debeTenerTokensDisponibles() {
        // El bucket global se crea con límites del perfil test (100 requests/min)
        assertThat(globalBucket.getAvailableTokens()).isPositive();
    }

    @Test
    void globalBucket_debeTenerCapacidad100() {
        // En application-test.yml: rate-limiting.global.capacity: 100
        assertThat(globalBucket.getAvailableTokens()).isEqualTo(100);
    }

    @Test
    void configGetters_debeDevolverValoresDeTest() {
        // En application-test.yml:
        //   rate-limiting.per-ip.capacity: 10
        //   rate-limiting.per-ip.refill-per-minute: 10
        assertThat(config.getPerIpCapacity()).isEqualTo(10);
        assertThat(config.getPerIpRefillPerMinute()).isEqualTo(10);
    }

    @Test
    void ipBuckets_debeCrearBucketBajoDemanda() {
        // Verificamos que el mapa de IPs acepta nuevas entradas
        // NOTA: limpiamos después para no afectar ipBuckets_debeEstarVacio
        // (Spring comparte el mismo bean entre tests del mismo contexto)
        ipBuckets.put("192.168.1.1", globalBucket);
        assertThat(ipBuckets).hasSize(1);
        assertThat(ipBuckets.get("192.168.1.1")).isSameAs(globalBucket);
        ipBuckets.clear();
    }
}
