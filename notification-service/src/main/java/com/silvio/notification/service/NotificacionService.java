package com.silvio.notification.service;

import com.silvio.notification.dto.NotificacionDTO;
import com.silvio.notification.dto.NotificacionRequestDTO;
import com.silvio.notification.model.Notificacion;
import com.silvio.notification.repository.NotificacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificacionService {

    private final NotificacionRepository notificacionRepository;

    public NotificacionDTO crear(NotificacionRequestDTO request) {
        log.info("Creando notificación tipo {} para usuario: {}",
                request.getTipo(), request.getUsuarioId());

        Notificacion notificacion = new Notificacion();
        notificacion.setUsuarioId(request.getUsuarioId());
        notificacion.setTipo(request.getTipo());
        notificacion.setMensaje(request.getMensaje());
        notificacion.setFechaEnvio(LocalDateTime.now());
        notificacion.setLeida(false);

        Notificacion guardada = notificacionRepository.save(notificacion);
        log.info("Notificación creada — id: {}, usuario: {}, tipo: {}",
                guardada.getId(), guardada.getUsuarioId(), guardada.getTipo());
        return mapearADto(guardada);
    }

    public List<NotificacionDTO> obtenerPorUsuario(String usuarioId) {
        log.info("Consultando notificaciones del usuario: {}", usuarioId);
        return notificacionRepository
                .findByUsuarioIdOrderByFechaEnvioDesc(usuarioId)
                .stream()
                .map(this::mapearADto)
                .collect(Collectors.toList());
    }

    public List<NotificacionDTO> obtenerNoLeidas(String usuarioId) {
        log.info("Consultando notificaciones no leídas del usuario: {}", usuarioId);
        return notificacionRepository
                .findByUsuarioIdAndLeidaFalse(usuarioId)
                .stream()
                .map(this::mapearADto)
                .collect(Collectors.toList());
    }

    public NotificacionDTO marcarLeida(Long id) {
        log.info("Marcando notificación como leída — id: {}", id);
        Notificacion notificacion = notificacionRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Notificación no encontrada — id: {}", id);
                    return new RuntimeException("Notificación no encontrada con id: " + id);
                });

        notificacion.setLeida(true);
        return mapearADto(notificacionRepository.save(notificacion));
    }

    public void marcarTodasLeidas(String usuarioId) {
        log.info("Marcando todas las notificaciones como leídas — usuario: {}", usuarioId);
        List<Notificacion> noLeidas = notificacionRepository
                .findByUsuarioIdAndLeidaFalse(usuarioId);
        noLeidas.forEach(n -> n.setLeida(true));
        notificacionRepository.saveAll(noLeidas);
        log.info("{} notificaciones marcadas como leídas para usuario: {}",
                noLeidas.size(), usuarioId);
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