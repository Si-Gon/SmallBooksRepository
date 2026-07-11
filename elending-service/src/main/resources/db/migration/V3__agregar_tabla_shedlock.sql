-- Tabla para ShedLock: bloqueo distribuido del scheduler multi-instancia
-- Cada instancia intenta adquirir el lock antes de ejecutar cerrarPrestamosVencidos
-- lock_until = momento hasta el cual el lock está tomado
-- locked_at  = momento en que se adquirió el lock
-- locked_by  = identificador de la instancia que tomó el lock
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
