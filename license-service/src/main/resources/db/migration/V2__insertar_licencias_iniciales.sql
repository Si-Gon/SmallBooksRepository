-- Licencias para los 5 libros iniciales del catálogo
-- Los IDs 1-5 corresponden a los libros insertados en catalog-service V2
-- total_copias = copias_disponibles al inicio (ningún préstamo activo)

INSERT INTO licencias (libro_id, total_copias, copias_disponibles) VALUES
(1, 5, 5),   -- Cien años de soledad: 5 copias
(2, 3, 3),   -- El Señor de los Anillos: 3 copias
(3, 5, 5),   -- 1984: 5 copias
(4, 3, 3),   -- Don Quijote: 3 copias
(5, 10, 10); -- El Principito: 10 copias