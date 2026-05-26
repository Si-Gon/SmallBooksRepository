package com.silvio.ingestion.repository;

import com.silvio.ingestion.model.ArchivoLibro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArchivoLibroRepository extends JpaRepository<ArchivoLibro, Long> {

    Optional<ArchivoLibro> findByLibroId(Long libroId);
    boolean existsByLibroId(Long libroId);
}