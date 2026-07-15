package com.microservice.gateway.microservice_gateway.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de regresión de seguridad en archivos YAML de configuración.
 *
 * Verifica que NO haya secretos hardcodeados en los archivos YAML
 * del proyecto. Si alguien reintroduce el valor plano en el futuro,
 * este test fallará en CI.
 *
 * NOTA: Estos tests NO dependen de Spring — trabajan directamente
 * sobre el sistema de archivos, por lo que se ejecutan rápido
 * y no requieren contexto.
 */
class SecurityConfigYamlTest {

    // Valor hardcodeado ANTES del fix — si aparece en algún YAML, el test falla
    private static final String OLD_HARDCODED_SECRET = "Duoc.1983Duoc.1983Duoc.1983Duoc.1983";

    // Archivos YAML de configuración centralizada (donde estaba el riesgo)
    private static final List<String> CONFIG_YAML_FILES = List.of(
            "microservice-config/src/main/resources/configurations/msvc-gateway.yml",
            "microservice-config/src/main/resources/configurations/identity-service.yml",
            "microservice-config/src/main/resources/application.yml"
    );

    // Archivos YAML locales de cada servicio
    private static final List<String> LOCAL_YAML_FILES = List.of(
            "microservice-gateway/src/main/resources/application.yml",
            "microservice-gateway/src/test/resources/application-test.yml",
            "identity-services/src/main/resources/application.yml",
            "identity-services/src/test/resources/application-test.yml"
    );

    /**
     * Encuentra la raíz del proyecto subiendo desde el directorio de trabajo.
     * La raíz es la carpeta que contiene pom.xml Y el directorio microservice-config
     * (indicador de que es el multi-módulo padre, no un submódulo).
     */
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
    void configCentralizada_noTieneSecretHardcodeado() throws IOException {
        Path projectRoot = findProjectRoot();
        assertThat(projectRoot).as("Debe encontrar la raíz del proyecto").isNotNull();

        for (String yamlPath : CONFIG_YAML_FILES) {
            Path fullPath = projectRoot.resolve(yamlPath);
            assertThat(fullPath).as("Archivo YAML debe existir: " + yamlPath).exists();

            String content = Files.readString(fullPath);

            assertThat(content)
                    .as("El archivo " + yamlPath + " NO debe contener el secret hardcodeado")
                    .doesNotContain(OLD_HARDCODED_SECRET);
        }
    }

    @Test
    void yamlLocales_noTienenSecretHardcodeado() throws IOException {
        Path projectRoot = findProjectRoot();
        assertThat(projectRoot).as("Debe encontrar la raíz del proyecto").isNotNull();

        for (String yamlPath : LOCAL_YAML_FILES) {
            Path fullPath = projectRoot.resolve(yamlPath);
            assertThat(fullPath).as("Archivo YAML debe existir: " + yamlPath).exists();

            String content = Files.readString(fullPath);

            assertThat(content)
                    .as("El archivo " + yamlPath + " NO debe contener el secret hardcodeado")
                    .doesNotContain(OLD_HARDCODED_SECRET);
        }
    }

    @Test
    void msvcGatewayYaml_jwtSecretUsaPlaceholder() throws IOException {
        Path projectRoot = findProjectRoot();
        Path gatewayYaml = projectRoot.resolve(
                "microservice-config/src/main/resources/configurations/msvc-gateway.yml");

        String content = Files.readString(gatewayYaml);

        assertThat(content)
                .as("msvc-gateway.yml debe usar ${JWT_SECRET} en lugar del valor hardcodeado")
                .contains("${JWT_SECRET}");
    }

    @Test
    void identityServiceYaml_jwtSecretUsaPlaceholder() throws IOException {
        Path projectRoot = findProjectRoot();
        Path identityYaml = projectRoot.resolve(
                "microservice-config/src/main/resources/configurations/identity-service.yml");

        String content = Files.readString(identityYaml);

        assertThat(content)
                .as("identity-service.yml debe usar ${JWT_SECRET} en lugar del valor hardcodeado")
                .contains("${JWT_SECRET}");
    }

    @Test
    void gatewayYaml_actuatorShowDetailsEsWhenAuthorized() throws IOException {
        // Verifica que show-details en Gateway esté en "when-authorized" (no "always")
        // Issue 1: Restrict Actuator endpoints — cambio de "always" a "when-authorized"
        Path projectRoot = findProjectRoot();
        assertThat(projectRoot).as("Debe encontrar la raíz del proyecto").isNotNull();

        Path gatewayYaml = projectRoot.resolve(
                "microservice-gateway/src/main/resources/application.yml");
        assertThat(gatewayYaml).as("Gateway application.yml debe existir").exists();

        String content = Files.readString(gatewayYaml);

        assertThat(content)
                .as("Gateway application.yml debe tener show-details: when-authorized")
                .contains("show-details: when-authorized");

        assertThat(content)
                .as("Gateway application.yml NO debe tener show-details: always")
                .doesNotContain("show-details: always");
    }

    @Test
    void ningunYamlTieneAllowedOriginsWildcard() throws IOException {
        // Verifica que ningún YAML tenga allowedOrigins: "*"
        Path projectRoot = findProjectRoot();
        assertThat(projectRoot).as("Debe encontrar la raíz del proyecto").isNotNull();

        List<Path> gatewayYamls = List.of(
                projectRoot.resolve("microservice-config/src/main/resources/configurations/msvc-gateway.yml"),
                projectRoot.resolve("microservice-gateway/src/main/resources/application.yml"),
                projectRoot.resolve("microservice-gateway/src/test/resources/application-test.yml")
        );

        for (Path yaml : gatewayYamls) {
            if (Files.exists(yaml)) {
                String content = Files.readString(yaml);
                assertThat(content)
                        .as(yaml.getFileName() + " no debe tener allowedOrigins con '*'")
                        .doesNotContain("allowedOrigins: \"*\"")
                        .doesNotContain("allowed-origins: \"*\"")
                        .doesNotContain("allowed-origins: *");
            }
        }
    }
}
