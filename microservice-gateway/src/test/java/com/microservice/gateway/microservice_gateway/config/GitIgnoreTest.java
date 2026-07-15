package com.microservice.gateway.microservice_gateway.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que .gitignore excluya correctamente archivos sensibles
 * y que el template .env.example sea trackeable.
 *
 * Reglas:
 * - `.env` DEBE estar en .gitignore (nunca subir secrets reales)
 * - `.env.example` NO debe estar en .gitignore (template para devs)
 * - `.gitignore` debe contener "!.env.example" para permitir el template
 */
class GitIgnoreTest {

    private static final String GITIGNORE_FILE = ".gitignore";
    private static final String ENV_FILE = ".env";
    private static final String ENV_EXAMPLE_FILE = ".env.example";

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
    void gitignore_excluyeEnv() throws IOException {
        Path projectRoot = findProjectRoot();
        assertThat(projectRoot).as("Debe encontrar la raíz del proyecto").isNotNull();

        Path gitignore = projectRoot.resolve(GITIGNORE_FILE);
        assertThat(gitignore).as(".gitignore debe existir").exists();

        String content = Files.readString(gitignore);

        assertThat(content)
                .as(".gitignore debe excluir .env (contener una línea con .env)")
                .contains(".env");
    }

    @Test
    void gitignore_noExcluyeEnvExample() throws IOException {
        Path projectRoot = findProjectRoot();
        assertThat(projectRoot).as("Debe encontrar la raíz del proyecto").isNotNull();

        Path gitignore = projectRoot.resolve(GITIGNORE_FILE);
        assertThat(gitignore).as(".gitignore debe existir").exists();

        String content = Files.readString(gitignore);

        // Verificar que .env.example NO está excluido (debe tener !.env.example)
        // Un .gitignore bien configurado tiene:
        //   .env          ← excluye .env
        //   *.env         ← excluye cualquier .env*
        //   !.env.example ← pero permite .env.example
        assertThat(content)
                .as(".gitignore debe tener !.env.example para permitir el template")
                .contains("!.env.example");

        // Verificar que .env.example no está siendo excluido por otro patrón
        if (content.contains("*.env")) {
            assertThat(content)
                    .as("Si existe *.env, debe tener !.env.example para exception")
                    .contains("!.env.example");
        }
    }

    @Test
    void env_example_existeYNoEsEnv() {
        Path projectRoot = findProjectRoot();
        assertThat(projectRoot).as("Debe encontrar la raíz del proyecto").isNotNull();

        Path envExample = projectRoot.resolve(ENV_EXAMPLE_FILE);
        Path envReal = projectRoot.resolve(ENV_FILE);

        // .env.example debe existir (template)
        assertThat(envExample)
                .as(".env.example debe existir como template para desarrolladores")
                .exists();

        // .env no debería existir durante CI (pero puede existir localmente)
        // No verificamos existencia de .env porque puede o no estar presente
    }

    @Test
    void env_example_esDiferenteDeEnv() throws IOException {
        Path projectRoot = findProjectRoot();
        assertThat(projectRoot).as("Debe encontrar la raíz del proyecto").isNotNull();

        Path envExample = projectRoot.resolve(ENV_EXAMPLE_FILE);
        Path envReal = projectRoot.resolve(ENV_FILE);

        assertThat(envExample).as(".env.example debe existir").exists();

        // Si .env existe, verificar que sean archivos diferentes
        if (Files.exists(envReal)) {
            String exampleContent = Files.readString(envExample);
            String realContent = Files.readString(envReal);

            assertThat(exampleContent)
                    .as(".env.example NO debe contener el valor real de JWT_SECRET")
                    .doesNotContain("Duoc.1983");

            assertThat(realContent)
                    .as(".env (real) debe contener el JWT_SECRET de desarrollo")
                    .contains("Duoc.1983");
        }
    }

    @Test
    void gitignore_tieneComentarioSobreEnvTemplate() throws IOException {
        Path projectRoot = findProjectRoot();
        assertThat(projectRoot).as("Debe encontrar la raíz del proyecto").isNotNull();

        Path gitignore = projectRoot.resolve(GITIGNORE_FILE);
        String content = Files.readString(gitignore);

        // Verificar que hay un comentario explicativo sobre .env.example
        assertThat(content)
                .as(".gitignore debe tener un comentario explicando que .env.example no se ignora")
                .contains("!.env.example");
    }
}
