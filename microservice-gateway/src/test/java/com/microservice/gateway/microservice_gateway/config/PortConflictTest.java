package com.microservice.gateway.microservice_gateway.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que no haya conflictos de puertos entre docker-compose.yml
 * y los archivos de configuración de los microservicios.
 *
 * Reglas:
 * - El único servicio con server.port que coincide con un puerto expuesto
 *   en Docker debe ser el gateway (8080).
 * - Ningún microservicio debe compartir el mismo server.port.
 * - Los puertos de infraestructura (9411 zipkin, 5672/15672 rabbitmq)
 *   no deben ser usados por ningún microservicio.
 */
class PortConflictTest {

    // Puertos expuestos al host por Docker
    private static final Map<Integer, String> DOCKER_EXPOSED_PORTS = Map.of(
            8080, "gateway",
            9411, "zipkin",
            5672, "rabbitmq (AMQP)",
            15672, "rabbitmq (Management UI)"
    );

    // Puertos de infraestructura que ningún microservicio debe usar
    private static final List<Integer> INFRASTRUCTURE_PORTS = List.of(9411, 5672, 15672);

    // Único servicio permitido en usar un puerto Docker-expuesto
    private static final String GATEWAY_SERVICE = "msvc-gateway";

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

    @Test
    void ningunMicroservicioUsaPuertoDeInfraestructura() throws IOException {
        Path projectRoot = findProjectRoot();
        assertThat(projectRoot).as("Debe encontrar la raíz del proyecto").isNotNull();

        Path configDir = projectRoot.resolve(
                "microservice-config/src/main/resources/configurations");
        assertThat(configDir).as("Directorio de configuraciones debe existir").isDirectory();

        List<Path> configFiles = Files.list(configDir)
                .filter(f -> f.toString().endsWith(".yml"))
                .collect(Collectors.toList());

        for (Path configFile : configFiles) {
            String content = Files.readString(configFile);
            Integer port = extractServerPort(content);

            if (port != null) {
                assertThat(port)
                        .as("El archivo " + configFile.getFileName() +
                                " NO debe usar un puerto de infraestructura (zipkin=9411, rabbitmq=5672/15672)")
                        .isNotIn(INFRASTRUCTURE_PORTS);
            }
        }
    }

    @Test
    void soloGatewayUsaPuertoExpuestoEnDocker() throws IOException {
        Path projectRoot = findProjectRoot();
        assertThat(projectRoot).as("Debe encontrar la raíz del proyecto").isNotNull();

        Path configDir = projectRoot.resolve(
                "microservice-config/src/main/resources/configurations");
        assertThat(configDir).as("Directorio de configuraciones debe existir").isDirectory();

        List<Path> configFiles = Files.list(configDir)
                .filter(f -> f.toString().endsWith(".yml"))
                .collect(Collectors.toList());

        for (Path configFile : configFiles) {
            String content = Files.readString(configFile);
            Integer port = extractServerPort(content);
            String fileName = configFile.getFileName().toString();

            if (port != null && DOCKER_EXPOSED_PORTS.containsKey(port)) {
                // Solo el gateway puede usar un puerto expuesto
                assertThat(fileName)
                        .as("Solo " + GATEWAY_SERVICE + " puede usar el puerto " + port +
                                " (expuesto en Docker como " + DOCKER_EXPOSED_PORTS.get(port) + ")" +
                                ". El archivo " + fileName + " también lo usa.")
                        .isEqualTo(GATEWAY_SERVICE + ".yml");
            }
        }
    }

    @Test
    void noHayPuertosDuplicadosEntreMicroservicios() throws IOException {
        Path projectRoot = findProjectRoot();
        assertThat(projectRoot).as("Debe encontrar la raíz del proyecto").isNotNull();

        Path configDir = projectRoot.resolve(
                "microservice-config/src/main/resources/configurations");
        assertThat(configDir).as("Directorio de configuraciones debe existir").isDirectory();

        // Recopilar todos los puertos de los archivos de configuración
        Map<Integer, List<String>> portToFiles = new HashMap<>();

        List<Path> configFiles = Files.list(configDir)
                .filter(f -> f.toString().endsWith(".yml"))
                .collect(Collectors.toList());

        for (Path configFile : configFiles) {
            String content = Files.readString(configFile);
            Integer port = extractServerPort(content);
            String fileName = configFile.getFileName().toString();

            if (port != null) {
                portToFiles.computeIfAbsent(port, k -> new ArrayList<>()).add(fileName);
            }
        }

        // Verificar que ningún puerto esté duplicado
        for (Map.Entry<Integer, List<String>> entry : portToFiles.entrySet()) {
            Integer port = entry.getKey();
            List<String> files = entry.getValue();

            assertThat(files)
                    .as("El puerto " + port + " está asignado a múltiples servicios: " + files)
                    .hasSize(1);
        }
    }

    /**
     * Extrae el server.port de un contenido YAML.
     * Busca el patrón "port: <número>" dentro de un bloque "server:".
     */
    private Integer extractServerPort(String yamlContent) {
        // Busca "port: <dígitos>" después de "server:"
        Pattern pattern = Pattern.compile(
                "(?m)^server:\\s*$.*?^port:\\s*(\\d+)\\s*$",
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(yamlContent);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }
}
