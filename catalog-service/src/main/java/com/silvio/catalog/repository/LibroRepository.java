package com.silvio.catalog.repository;

import com.silvio.catalog.model.Libro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("SELECT l FROM Libro l WHERE " +
           "(:titulo IS NULL OR LOWER(l.titulo) LIKE LOWER(CONCAT('%', :titulo, '%'))) AND " +
           "(:autor IS NULL OR LOWER(l.autor) LIKE LOWER(CONCAT('%', :autor, '%'))) AND " +
           "(:genero IS NULL OR LOWER(l.genero) = LOWER(:genero))")
    List<Libro> buscarCombinado(
            @Param("titulo") String titulo,
            @Param("autor") String autor,
            @Param("genero") String genero
    );
}