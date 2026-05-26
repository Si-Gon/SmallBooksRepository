CREATE TABLE archivos_libros (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    libro_id        BIGINT          NOT NULL UNIQUE,
    nombre_archivo  VARCHAR(255)    NOT NULL,
    formato         VARCHAR(10)     NOT NULL,
    tamanio         BIGINT          NOT NULL,
    ruta_o_clave    VARCHAR(500)    NOT NULL,
    fecha_subida    DATETIME        NOT NULL
);