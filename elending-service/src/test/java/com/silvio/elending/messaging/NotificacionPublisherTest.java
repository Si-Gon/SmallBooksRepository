package com.silvio.elending.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de NotificacionPublisher.
 *
 * Verifica que el publisher enrute cada tipo de evento al routing key
 * correcto del exchange "notificacion.exchange" y que maneje correctamente
 * los casos borde (tipo desconocido, null).
 */
@ExtendWith(MockitoExtension.class)
class NotificacionPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Captor
    private ArgumentCaptor<String> routingKeyCaptor;

    @Captor
    private ArgumentCaptor<NotificacionEvent> eventCaptor;

    private NotificacionPublisher publisher;

    private static final String EXCHANGE = "notificacion.exchange";

    @BeforeEach
    void setUp() {
        publisher = new NotificacionPublisher(rabbitTemplate);
    }

    // ─── routing keys conocidos ───────────────────────────────────────────────

    @Test
    void publicarEvento_conPrestamoCreado_routingKeyCorrecto() {
        // Given
        NotificacionEvent evento = new NotificacionEvent("u1", "PRESTAMO_CREADO", "Mensaje");

        // When
        publisher.publicarEvento(evento);

        // Then
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), routingKeyCaptor.capture(), eq(evento));
        assertEquals("notificacion.prestamo.creado", routingKeyCaptor.getValue());
    }

    @Test
    void publicarEvento_conVencido_routingKeyCorrecto() {
        // Given
        NotificacionEvent evento = new NotificacionEvent("u2", "VENCIDO", "Mensaje");

        // When
        publisher.publicarEvento(evento);

        // Then
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), routingKeyCaptor.capture(), eq(evento));
        assertEquals("notificacion.prestamo.vencido", routingKeyCaptor.getValue());
    }

    @Test
    void publicarEvento_conProximoVencer_routingKeyCorrecto() {
        // Given
        NotificacionEvent evento = new NotificacionEvent("u3", "PROXIMO_VENCER", "Mensaje");

        // When
        publisher.publicarEvento(evento);

        // Then
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), routingKeyCaptor.capture(), eq(evento));
        assertEquals("notificacion.prestamo.proximo-vencer", routingKeyCaptor.getValue());
    }

    // ─── routing key desconocido ──────────────────────────────────────────────

    @Test
    void publicarEvento_conTipoDesconocido_routingKeyDefault() {
        // Given — tipo que no existe en el switch
        NotificacionEvent evento = new NotificacionEvent("u4", "TIPO_INEXISTENTE", "Mensaje");

        // When
        publisher.publicarEvento(evento);

        // Then — debe usar la routing key genérica de desconocidos
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), routingKeyCaptor.capture(), eq(evento));
        assertEquals("notificacion.prestamo.desconocido", routingKeyCaptor.getValue());
    }

    @Test
    void publicarEvento_conTipoNulo_lanzaExcepcion() {
        // Given — tipo null (no existe en el switch, switch expression lanza NPE)
        NotificacionEvent evento = new NotificacionEvent("u5", null, "Mensaje");

        // When & Then — NullPointerException al evaluar evento.getTipo() en el switch
        assertThrows(NullPointerException.class, () -> publisher.publicarEvento(evento));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    void publicarEvento_conTipoVacio_routingKeyDefault() {
        // Given — tipo empty string (cae en default del switch)
        NotificacionEvent evento = new NotificacionEvent("u6", "", "Mensaje");

        // When
        publisher.publicarEvento(evento);

        // Then
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), routingKeyCaptor.capture(), eq(evento));
        assertEquals("notificacion.prestamo.desconocido", routingKeyCaptor.getValue());
    }

    // ─── casos borde en campos del evento ─────────────────────────────────────

    @Test
    void publicarEvento_conUsuarioIdVacio_pasaCorrectamente() {
        // Given — usuarioId vacío (debe publicarse igual)
        NotificacionEvent evento = new NotificacionEvent("", "PRESTAMO_CREADO", "Mensaje");

        // When
        publisher.publicarEvento(evento);

        // Then
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), anyString(), eventCaptor.capture());
        assertEquals("", eventCaptor.getValue().getUsuarioId());
    }

    @Test
    void publicarEvento_conMensajeNulo_pasaCorrectamente() {
        // Given — mensaje null (se publica igual, el consumidor decide)
        NotificacionEvent evento = new NotificacionEvent("u7", "PRESTAMO_CREADO", null);

        // When
        publisher.publicarEvento(evento);

        // Then
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), anyString(), eventCaptor.capture());
        assertNull(eventCaptor.getValue().getMensaje());
    }

    @Test
    void publicarEvento_conUsuarioIdNulo_pasaCorrectamente() {
        // Given — usuarioId null (se publica igual)
        NotificacionEvent evento = new NotificacionEvent(null, "VENCIDO", "Mensaje");

        // When
        publisher.publicarEvento(evento);

        // Then
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), anyString(), eventCaptor.capture());
        assertNull(eventCaptor.getValue().getUsuarioId());
    }

    // ─── verificación del exchange ────────────────────────────────────────────

    @Test
    void publicarEvento_siempreUsaExchangeCorrecto() {
        // Given — eventos de distintos tipos
        NotificacionEvent creado = new NotificacionEvent("u1", "PRESTAMO_CREADO", "Msg");
        NotificacionEvent vencido = new NotificacionEvent("u2", "VENCIDO", "Msg");

        // When
        publisher.publicarEvento(creado);
        publisher.publicarEvento(vencido);

        // Then — todos usan el mismo exchange
        verify(rabbitTemplate, times(2))
                .convertAndSend(eq("notificacion.exchange"), anyString(), any(Object.class));
    }
}
