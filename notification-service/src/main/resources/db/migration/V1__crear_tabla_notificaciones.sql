CREATE TABLE notificaciones (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id  VARCHAR(100)    NOT NULL,
    tipo        VARCHAR(30)     NOT NULL,
    mensaje     VARCHAR(500)    NOT NULL,
    fecha_envio DATETIME        NOT NULL,
    leida       TINYINT(1)      NOT NULL DEFAULT 0
);

CREATE INDEX idx_usuario_leida ON notificaciones(usuario_id, leida);