package com.silvio.license.repository;

import com.silvio.license.model.License;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LicenseRepository extends JpaRepository<License, Long> {

    // Buscar por libroId — usado por E-Lending para verificar disponibilidad
    Optional<License> findByLibroId(Long libroId);
}