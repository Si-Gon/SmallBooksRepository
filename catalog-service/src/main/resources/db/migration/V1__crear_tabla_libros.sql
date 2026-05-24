CREATE TABLE libros (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    titulo            VARCHAR(200)  NOT NULL,
    autor             VARCHAR(150)  NOT NULL,
    isbn              VARCHAR(20)   NOT NULL UNIQUE,
    editorial         VARCHAR(150),
    anio_publicacion  INT,
    idioma            VARCHAR(50),
    genero            VARCHAR(100),
    sinopsis          TEXT,
    portada_url       VARCHAR(500),
    disponible        TINYINT(1)    NOT NULL DEFAULT 1
);