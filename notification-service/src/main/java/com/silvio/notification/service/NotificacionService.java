package com.silvio.notification.service;

import com.silvio.notification.dto.NotificacionDTO;
import com.silvio.notification.dto.NotificacionRequestDTO;
import com.silvio.notification.model.Notificacion;
import com.silvio.notification.repository.NotificacionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificacionService {

    private final NotificacionRepository notificacionRepository;

    // -----------------------------------------------------------------------
    // Crear notificación — llamado por E-Lending via Feign
    // -----------------------------------------------------------------------
    public NotificacionDTO crear(NotificacionRequestDTO request) {
        Notificacion notificacion = new Notificacion();
        notificacion.setUsuarioId(request.getUsuarioId());
        notificacion.setTipo(request.getTipo());
        notificacion.setMensaje(request.getMensaje());
        notificacion.setFechaEnvio(LocalDateTime.now());
        notificacion.setLeida(false);

        return mapearADto(notificacionRepository.save(notificacion));
    }

    // -----------------------------------------------------------------------
    // Obtener todas las notificaciones de un usuario
    // -----------------------------------------------------------------------
    public List<NotificacionDTO> obtenerPorUsuario(String usuarioId) {
        return notificacionRepository
                .findByUsuarioIdOrderByFechaEnvioDesc(usuarioId)
                .stream()
                .map(this::mapearADto)
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Obtener solo las no leídas
    // -----------------------------------------------------------------------
    public List<NotificacionDTO> obtenerNoLeidas(String usuarioId) {
        return notificacionRepository
                .findByUsuarioIdAndLeidaFalse(usuarioId)
                .stream()
                .map(this::mapearADto)
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Marcar como leída
    // -----------------------------------------------------------------------
    public NotificacionDTO marcarLeida(Long id) {
        Notificacion notificacion = notificacionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Notificación no encontrada con id: " + id));

        notificacion.setLeida(true);
        return mapearADto(notificacionRepository.save(notificacion));
    }

    // -----------------------------------------------------------------------
    // Marcar todas como leídas
    // -----------------------------------------------------------------------
    public void marcarTodasLeidas(String usuarioId) {
        List<Notificacion> noLeidas = notificacionRepository
                .findByUsuarioIdAndLeidaFalse(usuarioId);

        noLeidas.forEach(n -> n.setLeida(true));
        notificacionRepository.saveAll(noLeidas);
    }

    private NotificacionDTO mapearADto(Notificacion n) {
        NotificacionDTO dto = new NotificacionDTO();
        dto.setId(n.getId());
        dto.setUsuarioId(n.getUsuarioId());
        dto.setTipo(n.getTipo());
        dto.setMensaje(n.getMensaje());
        dto.setFechaEnvio(n.getFechaEnvio());
        dto.setLeida(n.getLeida());
        return dto;
    }
}