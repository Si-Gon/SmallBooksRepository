package com.silvio.elending.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

// Publica eventos de notificación en RabbitMQ para que Notification Service
// los consuma de forma asíncrona. Reemplaza la llamada Feign síncrona.
// El exchange y las colas se declaran en notification-service (consumidor).
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificacionPublisher {

    private final RabbitTemplate rabbitTemplate;

    private static final String EXCHANGE = "notificacion.exchange";

    // Publica un evento en el exchange usando el routing key según el tipo
    public void publicarEvento(NotificacionEvent evento) {
        String routingKey = switch (evento.getTipo()) {
            case "PRESTAMO_CREADO" -> "notificacion.prestamo.creado";
            case "VENCIDO"         -> "notificacion.prestamo.vencido";
            case "PROXIMO_VENCER"  -> "notificacion.prestamo.proximo-vencer";
            default -> {
                log.warn("Tipo de notificación desconocido: {} — usando routing key genérica", evento.getTipo());
                yield "notificacion.prestamo.desconocido";
            }
        };

        rabbitTemplate.convertAndSend(EXCHANGE, routingKey, evento);
        log.info("Evento publicado — exchange: {}, routingKey: {}, tipo: {}, usuario: {}",
                EXCHANGE, routingKey, evento.getTipo(), evento.getUsuarioId());
    }
}
