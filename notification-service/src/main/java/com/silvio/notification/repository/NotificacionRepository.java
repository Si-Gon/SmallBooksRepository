package com.silvio.notification.repository;

import com.silvio.notification.model.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    // Todas las notificaciones de un usuario ordenadas por fecha descendente
    List<Notificacion> findByUsuarioIdOrderByFechaEnvioDesc(String usuarioId);

    // Solo las no leídas — para mostrar badge de notificaciones pendientes
    List<Notificacion> findByUsuarioIdAndLeidaFalse(String usuarioId);

    // Búsqueda por idempotencyKey para detectar duplicados (fast-path)
    Optional<Notificacion> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    // Marca todas las notificaciones no leídas de un usuario como leídas en una sola operación
    // clearAutomatically evita que el contexto de persistencia quede con entidades obsoletas
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notificacion n SET n.leida = true WHERE n.usuarioId = :usuarioId AND n.leida = false")
    int marcarTodasLeidasPorUsuario(@Param("usuarioId") String usuarioId);
}