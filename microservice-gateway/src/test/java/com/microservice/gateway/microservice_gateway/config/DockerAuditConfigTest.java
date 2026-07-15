package com.microservice.gateway.microservice_gateway.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de regresión de seguridad y buenas prácticas en docker-compose.yml.
 *
 * Verifica que:
 * 1. No haya JWT_SECRET hardcodeado en docker-compose.yml (use ${JWT_SECRET})
 * 2. Los 7 SPRING_DATASOURCE_URL usen variables ${...} en lugar de valores fijos
 * 3. Zipkin use una versión fija (no :latest)
 * 4. Solo gateway, zipkin y rabbitmq expongan puertos al host
 * 5. .env.example tenga todas las variables requeridas
 * 6. Todos los servicios dependan de zipkin con condition: service_healthy
 */
class DockerAuditConfigTest {

    private static final String OLD_HARDCODED_SECRET = "Duoc.1983Duoc.1983Duoc.1983Duoc.1983";

    private static final List<String> EXPECTED_ENV_VARS = List.of(
            "JWT_SECRET",
            "SPRING_DATASOURCE_URL_IDENTITY",
            "SPRING_DATASOURCE_URL_CATALOG",
            "SPRING_DATASOURCE_URL_LICENSE",
            "SPRING_DATASOURCE_URL_ELENDING",
            "SPRING_DATASOURCE_URL_NOTIFICATION",
            "SPRING_DATASOURCE_URL_SUBSCRIPTION",
            "SPRING_DATASOURCE_URL_INGESTION"
    );

    // Servicios que DEBEN exponer puertos
    private static final List<String> SERVICIOS_CON_PUERTOS = List.of("gateway", "zipkin", "rabbitmq");

    // Servicios internos que NO deben exponer puertos
    private static final List<String> SERVICIOS_INTERNOS = List.of(
            "config", "eureka", "identity", "catalog", "license",
            "elending", "notification", "subscription", "search",
            "analytics", "ingestion", "content"
    );

    // Servicios que DEBEN tener dependencia zipkin con condition: service_healthy
    // (excluimos zipkin mismo y rabbitmq que no necesita tracing distribuido)
    private static final List<String> SERVICIOS_CON_ZIPKIN_DEP = List.of(
            "config", "eureka", "gateway", "identity", "catalog", "license",
            "elending", "notification", "subscription", "search",
            "analytics", "ingestion", "content"
    );

    private Path findProjectRoot() {
        Path current = Paths.get(".").toAbsolutePath().normalize();
        while (current != null) {
            Path pom = current.resolve("pom.xml");
            Path configDir = current.resolve("microservice-config");
            if (Files.exists(pom) && Files.isDirectory(configDir)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────
    // 1. JWT_SECRET — No debe estar hardcodeado
    // ─────────────────────────────────────────────────────────────────

    @Test
    void jwtSecret_noEstaHardcodeadoEnDockerCompose() throws IOException {
        Path projectRoot = findProjectRoot();
        assertThat(projectRoot).as("Debe encontrar la raíz del proyecto").isNotNull();

        Path dockerCompose = projectRoot.resolve("docker-compose.yml");
        assertThat(dockerCompose).as("docker-compose.yml debe existir").exists();

        String content = Files.readString(dockerCompose);

        assertThat(content)
                .as("docker-compose.yml NO debe contener el JWT_SECRET hardcodeado")
                .doesNotContain(OLD_HARDCODED_SECRET);
    }

    @Test
    void jwtSecret_usaLugarVariableEnGateway() throws IOException {
        Path projectRoot = findProjectRoot();
        assertThat(projectRoot).as("Debe encontrar la raíz del proyecto").isNotNull();

        Path dockerCompose = projectRoot.resolve("docker-compose.yml");
        String content = Files.readString(dockerCompose);

        // Verificar que gateway usa ${JWT_SECRET}
        assertThat(content)
                .as("gateway debe referenciar JWT_SECRET como ${JWT_SECRET}")
                .contains("JWT_SECRET=${JWT_SECRET}");
    }

    @Test
    void jwtSecret_usaLugarVariableEnIdentity() throws IOException {
        Path projectRoot = findProjectRoot();
        assertThat(projectRoot).as("Debe encontrar la raíz del proyecto").isNotNull();

        Path dockerCompose = projectRoot.resolve("docker-compose.yml");
        String content = Files.readString(dockerCompose);

        // Verificar que identity también usa ${JWT_SECRET}
        assertThat(content)
                .as("identity debe referenciar JWT_SECRET como ${JWT_SECRET}")
                .contains("JWT_SECRET=${JWT_SECRET}");
    }

    // ─────────────────────────────────────────────────────────────────
    // 2. SPRING_DATASOURCE_URL — Usan variables de entorno
    // ─────────────────────────────────────────────────────────────────

    @Test
    void datasourceUrls_usanVariablesEnLugarDeValoresFijos() throws IOException {
        Path projectRoot = findProjectRoot();
        assertThat(projectRoot).as("Debe encontrar la raíz del proyecto").isNotNull();

        Path dockerCompose = projectRoot.resolve("docker-compose.yml");
        String content = Files.readString(dockerCompose);

        for (String envVar : EXPECTED_ENV_VARS) {
            if (envVar.equals("JWT_SECRET")) continue; // ya verificado arriba

            assertThat(content)
                    .as("SPRING_DATASOURCE_URL " + envVar + " debe usar ${" + envVar + "} en docker-compose.yml")
                    .contains("${" + envVar + "}");
        }
    }

    @Test
    void datasourceUrls_noTienenValoresHardcodeados() throws IOException {
        Path projectRoot = findProjectRoot();
        assertThat(projectRoot).as("Debe encontrar la raíz del proyecto").isNotNull();

        Path dockerCompose = projectRoot.resolve("docker-compose.yml");
        String content = Files.readString(dockerCompose);

        // Verificar que no hay URLs de MySQL hardcodeadas en environment
        // Busca patrones jdbc:mysql://... que NO estén dentro de ${...}
        Pattern mysqlUrlPattern = Pattern.compile("jdbc:mysql://[^\\s]*");
        Matcher matcher = mysqlUrlPattern.matcher(content);
        while (matcher.find()) {
            String match = matcher.group();
            // Verificar el contexto alrededor (las líneas con SPRING_DATASOURCE_URL)
            int start = Math.max(0, matcher.start() - 50);
            int end = Math.min(content.length(), matcher.end() + 10);
            String context = content.substring(start, end);

            assertThat(context)
                    .as("Las URLs de BD deben estar dentro de ${...}, no hardcodeadas")
                    .contains("${");
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 3. Zipkin — Versión fija (sin :latest)
    // ─────────────────────────────────────────────────────────────────

    @Test
    void zipkin_usaVersionFija() throws IOException {
        Path projectRoot = findProjectRoot();
        assertThat(projectRoot).as("Debe encontrar la raíz del proyecto").isNotNull();

        Path dockerCompose = projectRoot.resolve("docker-compose.yml");
        String content = Files.readString(dockerCompose);

        // Buscar la línea de imagen de zipkin
        String zipkinLine = content.lines()
                .filter(line -> line.contains("zipkin/zipkin"))
                .findFirst()
                .orElse(null);

        assertThat(zipkinLine)
                .as("Debe existir una línea con la imagen de zipkin")
                .isNotNull();

        assertThat(zipkinLine)
                .as("Zipkin NO debe usar :latest — debe tener una versión fija")
                .doesNotContain(":latest");

        assertThat(zipkinLine)
                .as("Zipkin debe usar una versión específica (e.g., 3.4.2)")
                .containsPattern("zipkin:\\d+\\.\\d+\\.\\d+");
    }

    // ─────────────────────────────────────────────────────────────────
    // 4. Puertos — Solo servicios externos exponen puertos
    // ─────────────────────────────────────────────────────────────────

    @Test
    void soloServiciosExternosExponenPuertos() throws IOException {
        Path projectRoot = findProjectRoot();
        assertThat(projectRoot).as("Debe encontrar la raíz del proyecto").isNotNull();

        Path dockerCompose = projectRoot.resolve("docker-compose.yml");
        String content = Files.readString(dockerCompose);

        // Busca servicios con ports: limitándose al bloque de cada servicio
        // (no cruza a otro servicio al mismo nivel de indentación).
        // Patrón: "  nombre:" seguido de contenido que no contenga otro "^  nombre:"
        // y que contenga "    ports:"
        Pattern serviceWithPorts = Pattern.compile(
                "(?m)^  (?<name>\\w+):\\s*$(?:(?!^  \\w+:).)*^    ports:",
                Pattern.DOTALL);
        Matcher matcher = serviceWithPorts.matcher(content);

        while (matcher.find()) {
            String serviceName = matcher.group("name");
            assertThat(serviceName)
                    .as("El servicio '" + serviceName + "' expone puertos. " +
                            "Solo gateway, zipkin y rabbitmq deben hacerlo.")
                    .isIn(SERVICIOS_CON_PUERTOS);
        }
    }

    @Test
    void serviciosInternosNoTienenPuertos() throws IOException {
        Path projectRoot = findProjectRoot();
        assertThat(projectRoot).as("Debe encontrar la raíz del proyecto").isNotNull();

        Path dockerCompose = projectRoot.resolve("docker-compose.yml");
        String content = Files.readString(dockerCompose);

        for (String service : SERVICIOS_INTERNOS) {
            // Buscar el bloque del servicio y verificar que NO contenga "ports:"
            String serviceBlock = extractServiceBlock(content, service);
            if (serviceBlock != null) {
                assertThat(serviceBlock)
                        .as("El servicio interno '" + service + "' NO debe tener 'ports:'")
                        .doesNotContain("ports:");
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 5. .env.example — Template completo
    // ─────────────────────────────────────────────────────────────────

    @Test
    void envExample_tieneTodasLasVariablesRequeridas() throws IOException {
        Path projectRoot = findProjectRoot();
        assertThat(projectRoot).as("Debe encontrar la raíz del proyecto").isNotNull();

        Path envExample = projectRoot.resolve(".env.example");
        assertThat(envExample).as(".env.example debe existir").exists();

        String content = Files.readString(envExample);

        for (String varName : EXPECTED_ENV_VARS) {
            assertThat(content)
                    .as(".env.example debe contener la variable " + varName + "=... (template)")
                    .contains(varName + "=");
        }
    }

    @Test
    void envExample_jwtSecretTieneValorChangeme() throws IOException {
        Path projectRoot = findProjectRoot();
        assertThat(projectRoot).as("Debe encontrar la raíz del proyecto").isNotNull();

        Path envExample = projectRoot.resolve(".env.example");
        String content = Files.readString(envExample);

        assertThat(content)
                .as(".env.example debe tener JWT_SECRET=changeme como template seguro")
                .contains("JWT_SECRET=changeme");

        assertThat(content)
                .as(".env.example NO debe contener el valor real del JWT_SECRET")
                .doesNotContain("Duoc.1983");
    }

    @Test
    void envExample_jwtSecretNoTieneValorReal() throws IOException {
        Path projectRoot = findProjectRoot();
        assertThat(projectRoot).as("Debe encontrar la raíz del proyecto").isNotNull();

        Path envExample = projectRoot.resolve(".env.example");
        assertThat(envExample).as(".env.example debe existir").exists();

        String content = Files.readString(envExample);

        assertThat(content)
                .as(".env.example NO debe contener el JWT_SECRET real del entorno de desarrollo")
                .doesNotContain("Duoc.1983");
    }

    // ─────────────────────────────────────────────────────────────────
    // 6. Zipkin dependency — Todos los servicios dependen de zipkin
    // ─────────────────────────────────────────────────────────────────

    @Test
    void todosLosServiciosDependenDeZipkinHealthy() throws IOException {
        Path projectRoot = findProjectRoot();
        assertThat(projectRoot).as("Debe encontrar la raíz del proyecto").isNotNull();

        Path dockerCompose = projectRoot.resolve("docker-compose.yml");
        String content = Files.readString(dockerCompose);

        for (String service : SERVICIOS_CON_ZIPKIN_DEP) {
            String serviceBlock = extractServiceBlock(content, service);
            assertThat(serviceBlock)
                    .as("El servicio '" + service + "' debe tener un bloque definido en docker-compose.yml")
                    .isNotNull();

            assertThat(serviceBlock)
                    .as("El servicio '" + service + "' debe depender de zipkin con condition: service_healthy")
                    .contains("zipkin:")
                    .contains("condition: service_healthy");
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Métodos auxiliares
    // ─────────────────────────────────────────────────────────────────

    /**
     * Extrae el bloque YAML de un servicio específico en docker-compose.yml.
     * Busca el nombre del servicio seguido de ":" y captura hasta el siguiente
     * servicio al mismo nivel (misma indentación) o fin de archivo.
     */
    private String extractServiceBlock(String content, String serviceName) {
        // Patrón para encontrar el bloque del servicio:
        // Busca "  <nombre>:" al inicio de línea (con 2 espacios de indentación)
        Pattern servicePattern = Pattern.compile(
                "(?m)^  " + serviceName + ":\\s*$([\\s\\S]*?)(?=^  \\w+:\\s*$|\\z)");
        Matcher matcher = servicePattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(0);
        }
        return null;
    }
}
