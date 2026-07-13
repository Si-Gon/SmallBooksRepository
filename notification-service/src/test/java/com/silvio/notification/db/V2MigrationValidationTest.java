package com.silvio.notification.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Validación del contenido del archivo de migración Flyway V2.
 *
 * Verifica que el script SQL contenga los elementos necesarios
 * para agregar la columna idempotency_key con UNIQUE INDEX.
 * Esto se hace leyendo el archivo SQL directamente (sin ejecutar
 * la migración contra H2, que no soporta totalmente la sintaxis MySQL).
 */
class V2MigrationValidationTest {

    private static final String MIGRATION_PATH =
            "src/main/resources/db/migration/V2__agregar_idempotency_key.sql";

    private List<String> leerMigracion(Path basePath) throws IOException {
        Path migrationFile = basePath.resolve(MIGRATION_PATH);
        assertThat(migrationFile)
                .as("El archivo de migración V2 debe existir")
                .exists();
        return Files.readAllLines(migrationFile);
    }

    @Test
    void v2_migration_contieneColumnaIdempotencyKey(@TempDir Path tempDir) throws Exception {
        // El tempDir no se usa directamente; usamos el directorio del proyecto
        Path projectDir = Paths.get(".").toRealPath();
        List<String> lineas = leerMigracion(projectDir);

        // Verificar que el ALTER TABLE incluya la columna idempotency_key
        assertThat(lineas)
                .as("La migración debe contener ALTER TABLE con idempotency_key")
                .anyMatch(linea -> linea.contains("idempotency_key"));

        // Verificar que la columna sea VARCHAR(64)
        assertThat(lineas)
                .as("idempotency_key debe ser VARCHAR(64)")
                .anyMatch(linea -> linea.contains("VARCHAR(64)"));

        // Verificar que sea NOT NULL
        assertThat(lineas)
                .as("idempotency_key debe ser NOT NULL")
                .anyMatch(linea -> linea.contains("NOT NULL"));
    }

    @Test
    void v2_migration_contieneUniqueIndex(@TempDir Path tempDir) throws Exception {
        Path projectDir = Paths.get(".").toRealPath();
        List<String> lineas = leerMigracion(projectDir);

        // Verificar que exista UNIQUE INDEX
        assertThat(lineas)
                .as("La migración debe crear UNIQUE INDEX sobre idempotency_key")
                .anyMatch(linea -> linea.contains("UNIQUE INDEX"));

        // Verificar el nombre del índice
        assertThat(lineas)
                .as("El UNIQUE INDEX debe llamarse idx_idempotency_key")
                .anyMatch(linea -> linea.contains("idx_idempotency_key"));
    }

    @Test
    void v2_migration_contieneColumnComment(@TempDir Path tempDir) throws Exception {
        Path projectDir = Paths.get(".").toRealPath();
        List<String> lineas = leerMigracion(projectDir);

        // Verificar que el comentario explique el propósito
        String contenido = String.join("\n", lineas);
        assertThat(contenido)
                .as("La migración debe documentar el propósito de idempotency_key")
                .contains("idempotency_key")
                .contains("duplicadas")
                .contains("RabbitMQ");
    }

    @Test
    void v2_migration_tieneFormatoCorrecto(@TempDir Path tempDir) throws Exception {
        Path projectDir = Paths.get(".").toRealPath();
        List<String> lineas = leerMigracion(projectDir);

        // Verificar que el SQL tenga estructura válida
        String contenido = String.join("\n", lineas);

        assertThat(contenido)
                .as("Debe comenzar con comentarios (--)")
                .contains("--");
        assertThat(contenido)
                .as("Debe contener ALTER TABLE")
                .contains("ALTER TABLE");
        assertThat(contenido)
                .as("Debe contener ADD COLUMN")
                .contains("ADD COLUMN");
        assertThat(contenido)
                .as("Debe contener ADD UNIQUE INDEX")
                .contains("ADD UNIQUE INDEX");
    }

    @Test
    void v2_migration_columnaSeAgregaDespuesDeLeida(@TempDir Path tempDir) throws Exception {
        Path projectDir = Paths.get(".").toRealPath();
        List<String> lineas = leerMigracion(projectDir);

        // Verificar que la columna se agregue AFTER leida (orden de columnas)
        assertThat(lineas)
                .as("La columna debe agregarse AFTER leida para mantener el orden en la tabla")
                .anyMatch(linea -> linea.contains("AFTER leida") || linea.contains("after leida"));
    }
}
