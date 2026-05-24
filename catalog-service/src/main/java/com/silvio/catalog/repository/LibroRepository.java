package com.silvio.catalog.repository;

import com.silvio.catalog.model.Libro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LibroRepository extends JpaRepository<Libro, Long> {

    // Búsqueda por título — contiene el texto, sin importar mayúsculas
    // Spring Data genera: SELECT * FROM libros WHERE titulo LIKE %?%
    List<Libro> findByTituloContainingIgnoreCase(String titulo);

    // Búsqueda por autor
    List<Libro> findByAutorContainingIgnoreCase(String autor);

    // Búsqueda por género
    List<Libro> findByGeneroIgnoreCase(String genero);

    // Listar solo los disponibles — usado por E-Lending para mostrar qué se puede prestar
    List<Libro> findByDisponibleTrue();

    // Verificar ISBN duplicado antes de guardar
    Optional<Libro> findByIsbn(String isbn);
}