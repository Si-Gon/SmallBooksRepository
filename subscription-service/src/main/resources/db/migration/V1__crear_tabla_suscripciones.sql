CREATE TABLE suscripciones (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id      VARCHAR(100)    NOT NULL UNIQUE,
    plan            VARCHAR(20)     NOT NULL,
    fecha_inicio    DATETIME        NOT NULL,
    fecha_fin       DATETIME        NOT NULL,
    activa          TINYINT(1)      NOT NULL DEFAULT 1
);

CREATE INDEX idx_usuario_activa ON suscripciones(usuario_id, activa);