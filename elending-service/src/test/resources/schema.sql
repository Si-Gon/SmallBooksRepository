-- Tabla para ShedLock en tests con H2.
-- Creada en schema.sql en lugar de @BeforeEach para que esté disponible
-- cuando el scheduler inicie tras cargar el contexto Spring.
-- Ver: db/migration/V3__agregar_tabla_shedlock.sql (producción con Flyway)
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
