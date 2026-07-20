package com.silvio.notification.service;

import com.silvio.notification.dto.NotificacionDTO;
import com.silvio.notification.dto.NotificacionRequestDTO;
import com.silvio.notification.exception.HashNoDisponibleException;
import com.silvio.notification.model.Notificacion;
import com.silvio.notification.repository.NotificacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.observation.annotation.Observed;
import java.util.NoSuchElementException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificacionService {

    private final NotificacionRepository notificacionRepository;

    @Observed(name = "notification.crear")
    @Transactional
    public NotificacionDTO crear(NotificacionRequestDTO request) {
        // Genera clave de idempotencia a partir del contenido del mensaje
        String idempotencyKey = generarIdempotencyKey(request);

        // Fast-path: verificar si ya se procesó este mensaje
        Optional<Notificacion> existente = notificacionRepository.findByIdempotencyKey(idempotencyKey);
        if (existente.isPresent()) {
            log.debug("Notificación duplicada ignorada — idempotencyKey: {}", idempotencyKey);
            return mapearADto(existente.get());
        }

        log.info("Creando notificación tipo {} para usuario: {}",
                request.getTipo(), request.getUsuarioId());

        Notificacion notificacion = new Notificacion();
        notificacion.setUsuarioId(request.getUsuarioId());
        notificacion.setTipo(request.getTipo());
        notificacion.setMensaje(request.getMensaje());
        notificacion.setFechaEnvio(LocalDateTime.now());
        notificacion.setLeida(false);
        notificacion.setIdempotencyKey(idempotencyKey);

        try {
            Notificacion guardada = notificacionRepository.save(notificacion);
            log.info("Notificación creada — id: {}, usuario: {}, tipo: {}",
                    guardada.getId(), guardada.getUsuarioId(), guardada.getTipo());
            return mapearADto(guardada);
        } catch (DataIntegrityViolationException e) {
            // Safe-net: otro nodo o reintento guardó este idempotencyKey primero
            log.debug("Notificación duplicada ignorada (race condition) — idempotencyKey: {}", idempotencyKey);
            return mapearADto(notificacionRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException(
                            "Inconsistencia: idempotencyKey duplicado sin registro — key: " + idempotencyKey)));
        }
    }

    // Genera un hash SHA-256 de (usuarioId + "|" + tipo + "|" + mensaje)
    // para usar como clave única de idempotencia.
    private String generarIdempotencyKey(NotificacionRequestDTO request) {
        String raw = request.getUsuarioId() + "|" + request.getTipo().name() + "|" + request.getMensaje();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new HashNoDisponibleException(e);
        }
    }

    @Observed(name = "notification.obtenerPorUsuario")
    @Transactional(readOnly = true)
    public List<NotificacionDTO> obtenerPorUsuario(String usuarioId) {
        log.info("Consultando notificaciones del usuario: {}", usuarioId);
        return notificacionRepository
                .findByUsuarioIdOrderByFechaEnvioDesc(usuarioId)
                .stream()
                .map(this::mapearADto)
                .collect(Collectors.toList());
    }

    @Observed(name = "notification.obtenerNoLeidas")
    @Transactional(readOnly = true)
    public List<NotificacionDTO> obtenerNoLeidas(String usuarioId) {
        log.info("Consultando notificaciones no leídas del usuario: {}", usuarioId);
        return notificacionRepository
                .findByUsuarioIdAndLeidaFalse(usuarioId)
                .stream()
                .map(this::mapearADto)
                .collect(Collectors.toList());
    }

    @Observed(name = "notification.marcarLeida")
    public NotificacionDTO marcarLeida(Long id) {
        log.info("Marcando notificación como leída — id: {}", id);
        Notificacion notificacion = notificacionRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Notificación no encontrada — id: {}", id);
                    return new NoSuchElementException("Notificación no encontrada con id: " + id);
                });

        notificacion.setLeida(true);
        return mapearADto(notificacionRepository.save(notificacion));
    }

    @Observed(name = "notification.marcarTodasLeidas")
    @Transactional
    public void marcarTodasLeidas(String usuarioId) {
        log.info("Marcando todas las notificaciones como leídas — usuario: {}", usuarioId);
        int actualizadas = notificacionRepository.marcarTodasLeidasPorUsuario(usuarioId);
        log.info("{} notificaciones marcadas como leídas para usuario: {}",
                actualizadas, usuarioId);
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