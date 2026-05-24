package com.silvio.subscription.repository;

import com.silvio.subscription.model.Suscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SuscripcionRepository extends JpaRepository<Suscripcion, Long> {

    Optional<Suscripcion> findByUsuarioId(String usuarioId);
    Optional<Suscripcion> findByUsuarioIdAndActivaTrue(String usuarioId);
}