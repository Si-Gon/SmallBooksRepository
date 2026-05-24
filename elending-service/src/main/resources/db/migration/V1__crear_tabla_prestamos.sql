CREATE TABLE prestamos (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id          VARCHAR(100)    NOT NULL,
    libro_id            BIGINT          NOT NULL,
    fecha_inicio        DATETIME        NOT NULL,
    fecha_vencimiento   DATETIME        NOT NULL,
    estado              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVO'
);

-- Índices para las queries más frecuentes del scheduler y del service
CREATE INDEX idx_estado ON prestamos(estado);
CREATE INDEX idx_usuario_estado ON prestamos(usuario_id, estado);
CREATE INDEX idx_vencimiento ON prestamos(fecha_vencimiento, estado);