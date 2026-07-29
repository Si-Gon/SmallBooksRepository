package com.silvio.elending.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de integración: valida Zipkin + RabbitMQ + Circuit Breaker trabajando juntos.
 * Requiere: docker-compose -f docker-compose.test.yml up -d --wait (antes de correr)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PipelineIntegrationTest {

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String CATALOG_URL = "http://localhost:8085";
    private static final String ELENDING_URL = "http://localhost:8087";
    private static final String INGESTION_URL = "http://localhost:8092";
    private static final String CONTENT_URL = "http://localhost:8093";
    private static final String ZIPKIN_URL = "http://localhost:9411";
    private static final String LICENSE_URL = "http://localhost:8086";

    private static String adminUser;
    private static String userId;
    private static String seedTimestamp;

    private static Long libroId;

    // ─── Seeding ─────────────────────────────────────────────────────────────

    @BeforeAll
    static void seedData() throws Exception {
        // IDs únicos por ejecución para evitar colisión con datos de runs anteriores
        seedTimestamp = String.valueOf(System.currentTimeMillis());
        adminUser = "admin-" + seedTimestamp;
        userId = "user-" + seedTimestamp;

        // Generar ISBN único por ejecución (978 + 10 dígitos = 13 dígitos)
        String isbnUnico = "978" + String.format("%010d", System.currentTimeMillis() % 10_000_000_000L);

        // 1. Crear libro en catalog-service
        String libroJson = """
            {"titulo":"Libro Test Pipeline seed-%s","autor":"Autor Test",
             "isbn":"%s","editorial":"Editorial Test",
             "anioPublicacion":2024,"idioma":"es","genero":"Test",
             "sinopsis":"Libro para test de integración"}
            """.formatted(seedTimestamp, isbnUnico);

        HttpResponse<String> catalogResp = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create(CATALOG_URL + "/api/catalog"))
                .header("Content-Type", "application/json")
                .header("X-User-Id", adminUser)
                .header("X-User-Roles", "ROLE_ADMIN")
                .POST(HttpRequest.BodyPublishers.ofString(libroJson))
                .build(),
            HttpResponse.BodyHandlers.ofString());

        assertEquals(201, catalogResp.statusCode(), "No se pudo crear libro seed: " + catalogResp.body());
        JsonNode libroNode = mapper.readTree(catalogResp.body());
        libroId = libroNode.get("id").asLong();

        // 1.5. Crear licencia/stock en license-service (paso obligatorio, no automático)
        String licenciaJson = "{\"libroId\":" + libroId + ",\"totalCopias\":5}";
        HttpResponse<String> licenciaResp = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create(LICENSE_URL + "/api/licenses"))
                .header("Content-Type", "application/json")
                .header("X-User-Id", adminUser)
                .header("X-User-Roles", "ROLE_ADMIN")
                .POST(HttpRequest.BodyPublishers.ofString(licenciaJson))
                .build(),
            HttpResponse.BodyHandlers.ofString());

        assertEquals(201, licenciaResp.statusCode(), "No se pudo crear licencia seed: " + licenciaResp.body());

        // 2. Crear préstamo activo en elending-service
        String prestamoJson = "{\"libroId\":" + libroId + "}";
        HttpResponse<String> prestamoResp = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create(ELENDING_URL + "/api/lending/prestamos"))
                .header("Content-Type", "application/json")
                .header("X-User-Id", userId)
                .POST(HttpRequest.BodyPublishers.ofString(prestamoJson))
                .build(),
            HttpResponse.BodyHandlers.ofString());

        assertEquals(201, prestamoResp.statusCode(), "No se pudo crear préstamo seed: " + prestamoResp.body());

        // 3. Subir archivo dummy en ingestion-service
        String boundary = "----TestBoundary" + System.currentTimeMillis();
        String multipartBody = "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"archivo\"; filename=\"test.pdf\"\r\n"
            + "Content-Type: application/pdf\r\n\r\n"
            + "%PDF-1.4 contenido dummy de test\r\n"
            + "--" + boundary + "--\r\n";

        HttpResponse<String> uploadResp = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create(INGESTION_URL + "/api/ingestion/upload/" + libroId))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("X-User-Id", adminUser)
                .POST(HttpRequest.BodyPublishers.ofString(multipartBody))
                .build(),
            HttpResponse.BodyHandlers.ofString());

        assertEquals(201, uploadResp.statusCode(), "No se pudo subir archivo seed: " + uploadResp.body());
    }

    // ─── 1. Camino feliz: descarga con préstamo activo ──────────────────────

    @Test
    @Order(1)
    void descargaArchivo_conPrestamoActivo_devuelve200ConBytes() throws Exception {
        HttpResponse<byte[]> resp = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create(CONTENT_URL + "/api/content/" + libroId))
                .header("X-User-Id", userId)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().length > 0, "El archivo no debe venir vacío en el camino feliz");
    }

    // ─── 2. Zipkin: la request anterior debe haber quedado trazada ─────────

    @Test
    @Order(2)
    void trazaDeDescarga_quedaRegistradaEnZipkin() throws Exception {
        // Zipkin indexa de forma asíncrona — pequeño margen antes de consultar
        TimeUnit.SECONDS.sleep(3);

        HttpResponse<String> resp = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create(ZIPKIN_URL + "/api/v2/traces?serviceName=content-service&limit=10"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

        assertEquals(200, resp.statusCode());
        JsonNode traces = mapper.readTree(resp.body());
        assertTrue(traces.isArray() && traces.size() > 0,
            "Debe existir al menos un trace de content-service en Zipkin");

        // Verificar que el trace incluye spans de los servicios downstream (elending, ingestion)
        boolean tieneSpanElending = false;
        boolean tieneSpanIngestion = false;
        for (JsonNode trace : traces) {
            for (JsonNode span : trace) {
                String serviceName = span.path("localEndpoint").path("serviceName").asText("");
                if (serviceName.contains("elending")) tieneSpanElending = true;
                if (serviceName.contains("ingestion")) tieneSpanIngestion = true;
            }
        }
        assertTrue(tieneSpanElending, "El trace debe incluir un span de elending-service");
        assertTrue(tieneSpanIngestion, "El trace debe incluir un span de ingestion-service");
    }

    // ─── 3. Circuit Breaker: elending caído → fallback → 403 ───────────────

    @Test
    @Order(3)
    void elendingCaido_activaCircuitBreaker_devuelve403() throws Exception {
        detenerContenedor("elending-test");
        try {
            // Reintentos hasta que el breaker abra (requests fallidas antes del threshold pasan directo)
            HttpResponse<String> resp = null;
            for (int i = 0; i < 15; i++) {
                resp = client.send(
                    HttpRequest.newBuilder()
                        .uri(URI.create(CONTENT_URL + "/api/content/" + libroId))
                        .header("X-User-Id", userId)
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 403) break;
                TimeUnit.SECONDS.sleep(1);
            }
            assertNotNull(resp);
            assertEquals(403, resp.statusCode(),
                "Con elending caído, el fallback debe devolver lista vacía → AccesoDenegadoException → 403");
        } finally {
            reiniciarContenedor("elending-test");
            esperarHealthy(ELENDING_URL, 60);
        }
    }

    // ─── 4. Circuit Breaker: el evento de fallo también queda trazado ──────

    @Test
    @Order(4)
    void aperturaDeCircuitBreaker_quedaTrazadaEnZipkin() throws Exception {
        TimeUnit.SECONDS.sleep(3);

        HttpResponse<String> resp = client.send(
            HttpRequest.newBuilder()
                .uri(URI.create(ZIPKIN_URL + "/api/v2/traces?serviceName=content-service&limit=20"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());

        JsonNode traces = mapper.readTree(resp.body());
        boolean encontroSpanConError = false;
        for (JsonNode trace : traces) {
            for (JsonNode span : trace) {
                if (span.path("tags").has("error")) {
                    encontroSpanConError = true;
                    break;
                }
            }
        }
        assertTrue(encontroSpanConError,
            "Debe existir al menos un span marcado con error tras la apertura del Circuit Breaker");
    }

    // ─── Helpers: control de contenedores Docker ────────────────────────────

    private static void detenerContenedor(String nombreContenedor) throws IOException, InterruptedException {
        ejecutarComando("docker", "stop", nombreContenedor);
    }

    private static void reiniciarContenedor(String nombreContenedor) throws IOException, InterruptedException {
        ejecutarComando("docker", "start", nombreContenedor);
    }

    private static void ejecutarComando(String... comando) throws IOException, InterruptedException {
        Process proceso = new ProcessBuilder(comando).inheritIO().start();
        boolean terminado = proceso.waitFor(30, TimeUnit.SECONDS);
        assertTrue(terminado, "Comando docker no terminó a tiempo: " + String.join(" ", comando));
        assertEquals(0, proceso.exitValue(), "Comando docker falló: " + String.join(" ", comando));
    }

    private static void esperarHealthy(String baseUrl, int timeoutSegundos) throws Exception {
        long limite = System.currentTimeMillis() + timeoutSegundos * 1000L;
        while (System.currentTimeMillis() < limite) {
            try {
                HttpResponse<String> resp = client.send(
                    HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/actuator/health"))
                        .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200 && resp.body().contains("\"status\":\"UP\"")) return;
            } catch (Exception ignored) { }
            TimeUnit.SECONDS.sleep(2);
        }
        fail("elending-test no volvió a estar healthy dentro de " + timeoutSegundos + "s");
    }
}
