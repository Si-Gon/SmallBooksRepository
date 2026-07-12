package com.silvio.notification.messaging;

import com.silvio.notification.dto.NotificacionRequestDTO;
import com.silvio.notification.model.Notificacion.TipoNotificacion;
import com.silvio.notification.service.NotificacionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de NotificacionEventListener.
 *
 * Verifica que el listener convierta correctamente los eventos RabbitMQ
 * en llamadas al servicio de notificaciones y que propague excepciones
 * para que el retry interceptor + DLQ manejen los fallos.
 *
 * El listener:
 *   1. Recibe un NotificacionEvent desde RabbitMQ
 *   2. Convierte el tipo (String) al enum TipoNotificacion
 *   3. Construye un NotificacionRequestDTO
 *   4. Llama a notificacionService.crear()
 * Si el tipo es inválido → IllegalArgumentException → DLQ
 * Si el servicio falla → RuntimeException → retry (3 intentos) → DLQ
 */
@ExtendWith(MockitoExtension.class)
class NotificacionEventListenerTest {

    @Mock
    private NotificacionService notificacionService;

    @InjectMocks
    private NotificacionEventListener listener;

    @Captor
    private ArgumentCaptor<NotificacionRequestDTO> requestCaptor;

    // ─── flujo exitoso — todos los tipos ──────────────────────────────────────

    @Test
    void procesarEvento_conPrestamoCreado_llamaServicio() {
        // Given
        NotificacionEvent evento = new NotificacionEvent("u1", "PRESTAMO_CREADO", "Tu préstamo fue creado");

        // When
        listener.procesarEvento(evento);

        // Then
        verify(notificacionService).crear(requestCaptor.capture());
        NotificacionRequestDTO request = requestCaptor.getValue();
        assertEquals("u1", request.getUsuarioId());
        assertEquals(TipoNotificacion.PRESTAMO_CREADO, request.getTipo());
        assertEquals("Tu préstamo fue creado", request.getMensaje());
    }

    @Test
    void procesarEvento_conVencido_llamaServicio() {
        // Given
        NotificacionEvent evento = new NotificacionEvent("u2", "VENCIDO", "Préstamo vencido");

        // When
        listener.procesarEvento(evento);

        // Then
        verify(notificacionService).crear(requestCaptor.capture());
        assertEquals(TipoNotificacion.VENCIDO, requestCaptor.getValue().getTipo());
    }

    @Test
    void procesarEvento_conProximoVencer_llamaServicio() {
        // Given
        NotificacionEvent evento = new NotificacionEvent("u3", "PROXIMO_VENCER", "Vence en 2 días");

        // When
        listener.procesarEvento(evento);

        // Then
        verify(notificacionService).crear(requestCaptor.capture());
        assertEquals(TipoNotificacion.PROXIMO_VENCER, requestCaptor.getValue().getTipo());
    }

    // ─── tipo inválido → IllegalArgumentException → DLQ ───────────────────────

    @Test
    void procesarEvento_conTipoInvalido_lanzaIllegalArgumentException() {
        // Given — tipo que no existe en el enum
        NotificacionEvent evento = new NotificacionEvent("u4", "TIPO_FICTICIO", "Mensaje");

        // When & Then — IllegalArgumentException se propaga al contenedor
        // El RejectAndDontRequeueRecoverer captura esto y envía el mensaje a DLQ
        assertThrows(IllegalArgumentException.class, () -> listener.procesarEvento(evento));
        verify(notificacionService, never()).crear(any());
    }

    @Test
    void procesarEvento_conTipoNulo_lanzaNullPointerException() {
        // Given — tipo null (valueOf(null) lanza NPE)
        NotificacionEvent evento = new NotificacionEvent("u5", null, "Mensaje");

        // When & Then — NPE antes de llegar al servicio
        assertThrows(NullPointerException.class, () -> listener.procesarEvento(evento));
        verify(notificacionService, never()).crear(any());
    }

    @Test
    void procesarEvento_conTipoVacio_lanzaIllegalArgumentException() {
        // Given — tipo empty string (no matchea ningún enum)
        NotificacionEvent evento = new NotificacionEvent("u6", "", "Mensaje");

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> listener.procesarEvento(evento));
        verify(notificacionService, never()).crear(any());
    }

    // ─── fallo del servicio → RuntimeException → retry → DLQ ─────────────────

    @Test
    void procesarEvento_cuandoServicioFalla_lanzaExcepcion() {
        // Given — el servicio lanza RuntimeException
        NotificacionEvent evento = new NotificacionEvent("u7", "PRESTAMO_CREADO", "Mensaje");
        doThrow(new RuntimeException("Error al guardar en BD"))
                .when(notificacionService).crear(any());

        // When & Then — la excepción se propaga para que el retry interceptor actúe
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> listener.procesarEvento(evento));
        assertTrue(ex.getMessage().contains("Error al guardar"));
        verify(notificacionService).crear(any());
    }

    // ─── casos borde en campos del evento ─────────────────────────────────────

    @Test
    void procesarEvento_conUsuarioIdVacio_pasaAlServicio() {
        // Given — usuarioId vacío (el servicio valida con @NotBlank)
        NotificacionEvent evento = new NotificacionEvent("", "PRESTAMO_CREADO", "Mensaje");

        // When
        listener.procesarEvento(evento);

        // Then — pasa al servicio (la validación ocurre en la capa de servicio)
        verify(notificacionService).crear(requestCaptor.capture());
        assertEquals("", requestCaptor.getValue().getUsuarioId());
    }

    @Test
    void procesarEvento_conMensajeNulo_pasaAlServicio() {
        // Given — mensaje null
        NotificacionEvent evento = new NotificacionEvent("u8", "VENCIDO", null);

        // When
        listener.procesarEvento(evento);

        // Then
        verify(notificacionService).crear(requestCaptor.capture());
        assertNull(requestCaptor.getValue().getMensaje());
    }

    @Test
    void procesarEvento_conUsuarioIdNulo_pasaAlServicio() {
        // Given — usuarioId null
        NotificacionEvent evento = new NotificacionEvent(null, "PROXIMO_VENCER", "Mensaje");

        // When
        listener.procesarEvento(evento);

        // Then
        verify(notificacionService).crear(requestCaptor.capture());
        assertNull(requestCaptor.getValue().getUsuarioId());
    }

    // ─── DLQ flow — casos extremos que deben propagarse al recoverer ───────────

    @Test
    void procesarEvento_conTipoValidoPeroNoEnum_lanzaIllegalArgumentException() {
        // Given — tipo string válido que NO corresponde a ningún enum
        // "PRESTAMO_CREADO" existe, "PRESTAMO_CREADO_EXTRA" no existe
        NotificacionEvent evento = new NotificacionEvent("u9", "PRESTAMO_CREADO_EXTRA", "Msg");

        // When & Then — debe lanzar IllegalArgumentException
        // RejectAndDontRequeueRecoverer captura esto → mensaje a DLQ
        assertThrows(IllegalArgumentException.class,
                () -> listener.procesarEvento(evento));
        verify(notificacionService, never()).crear(any());
    }

    @Test
    void procesarEvento_conMensajeYUsuarioNull_lanzaException() {
        // Given — todos los campos null excepto tipo
        // usuarioId = null no causa error directo, pero es un borde
        NotificacionEvent evento = new NotificacionEvent(null, "PRESTAMO_CREADO", null);

        // When — debe pasar porque el listener no valida nulls
        listener.procesarEvento(evento);

        // Then — el servicio recibe los nulls
        verify(notificacionService).crear(requestCaptor.capture());
        assertNull(requestCaptor.getValue().getUsuarioId());
        assertNull(requestCaptor.getValue().getMensaje());
        assertEquals(TipoNotificacion.PRESTAMO_CREADO, requestCaptor.getValue().getTipo());
    }

    // ─── mapeo exacto de campos ───────────────────────────────────────────────

    @Test
    void procesarEvento_mapeaExactamenteTodosLosCampos() {
        // Given
        NotificacionEvent evento = new NotificacionEvent("usuario_final", "PRESTAMO_CREADO",
                "Tu préstamo del libro 42 ha sido creado exitosamente. Tienes 7 días para leerlo.");

        // When
        listener.procesarEvento(evento);

        // Then — verifica mapeo 1:1 de los tres campos
        verify(notificacionService).crear(requestCaptor.capture());
        NotificacionRequestDTO request = requestCaptor.getValue();
        assertEquals(evento.getUsuarioId(), request.getUsuarioId());
        assertEquals(TipoNotificacion.PRESTAMO_CREADO, request.getTipo());
        assertEquals(evento.getMensaje(), request.getMensaje());
    }
}
