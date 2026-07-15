-- Índices para optimizar las consultas más frecuentes del catálogo
-- Los métodos del LibroRepository filtran por estos campos individualmente
-- y la búsqueda combinada usa titulo, autor, genero como filtros dinámicos

-- Índice para búsqueda por título (findByTituloContainingIgnoreCase, buscarCombinado)
CREATE INDEX idx_libros_titulo ON libros (titulo);

-- Índice para búsqueda por autor (findByAutorContainingIgnoreCase, buscarCombinado)
CREATE INDEX idx_libros_autor ON libros (autor);

-- Índice para búsqueda por género (findByGeneroIgnoreCase, buscarCombinado)
CREATE INDEX idx_libros_genero ON libros (genero);

-- Índice para filtrar libros disponibles (findByDisponibleTrue)
CREATE INDEX idx_libros_disponible ON libros (disponible);

-- Índice compuesto para la combinación más frecuente: disponible + género
-- Beneficia a E-Lending cuando consulta libros disponibles de un género específico
CREATE INDEX idx_libros_disponible_genero ON libros (disponible, genero);
