CREATE TABLE licencias (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    libro_id             BIGINT      NOT NULL UNIQUE,
    total_copias         INT         NOT NULL,
    copias_disponibles   INT         NOT NULL
);