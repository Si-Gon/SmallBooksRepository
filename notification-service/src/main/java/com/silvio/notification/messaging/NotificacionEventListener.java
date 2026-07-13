package com.silvio.notification.messaging;

import com.silvio.notification.dto.NotificacionRequestDTO;
import com.silvio.notification.model.Notificacion.TipoNotificacion;
import com.silvio.notification.service.NotificacionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import io.micrometer.observation.annotation.Observed;

// Consumidor de eventos de notificación desde RabbitMQ.
// Escucha la cola "notificacion.queue" y procesa los eventos
// publicados por E-Lending Service cuando ocurren operaciones
// de préstamo (creado, vencido, próximo a vencer).
//
// Reemplaza la llamada Feign síncrona desde E-Lending Service.
// Ahora E-Lending publica eventos en RabbitMQ y este listener
// los consume de forma asíncrona desde la cola "notificacion.queue".
//
// Si el procesamiento falla tras 3 reintentos, el mensaje se
// envía automáticamente a la Dead Letter Queue "notificacion.queue.dlq"
// para revisión manual o reprocesamiento posterior.
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificacionEventListener {

    private final NotificacionService notificacionService;

    @Observed(name = "notification.procesarEvento")
    @RabbitListener(queues = "notificacion.queue")
    public void procesarEvento(NotificacionEvent evento) {
        log.info("Evento recibido — tipo: {}, usuario: {}",
                evento.getTipo(), evento.getUsuarioId());

        // Convertir el String del evento al enum del notification-service
        TipoNotificacion tipo;
        try {
            tipo = TipoNotificacion.valueOf(evento.getTipo());
        } catch (IllegalArgumentException e) {
            log.error("Tipo de notificación inválido: {} — mensaje rechazado", evento.getTipo());
            throw e; // RejectAndDontRequeueRecoverer → envía a DLQ
        }

        NotificacionRequestDTO request = new NotificacionRequestDTO();
        request.setUsuarioId(evento.getUsuarioId());
        request.setTipo(tipo);
        request.setMensaje(evento.getMensaje());

        notificacionService.crear(request);

        log.info("Notificación procesada — tipo: {}, usuario: {}", tipo, evento.getUsuarioId());
    }
}
