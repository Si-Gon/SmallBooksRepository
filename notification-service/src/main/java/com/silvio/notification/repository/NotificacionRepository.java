package com.silvio.notification.repository;

import com.silvio.notification.model.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    // Todas las notificaciones de un usuario ordenadas por fecha descendente
    List<Notificacion> findByUsuarioIdOrderByFechaEnvioDesc(String usuarioId);

    // Solo las no leídas — para mostrar badge de notificaciones pendientes
    List<Notificacion> findByUsuarioIdAndLeidaFalse(String usuarioId);
}