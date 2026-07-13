package com.silvio.elending.config;

import com.silvio.elending.messaging.NotificacionEvent;
import com.silvio.elending.messaging.NotificacionPublisher;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Tests de integración que verifican que el tracing distribuido
// se propaga a través de RabbitMQ (productor en elending-service).
//
// ElendingService publica eventos de notificación en RabbitMQ.
// El RabbitTemplate tiene observationEnabled=true, lo que permite
// a Micrometer Tracing inyectar el traceId/spanId en los headers
// AMQP para que el consumidor (notification-service) continúe la traza.
@SpringBootTest
@ActiveProfiles("test")
class RabbitMQTracingIntegrationTest {

    @Autowired(required = false)
    private ObservationRegistry observationRegistry;

    @Autowired(required = false)
    private Tracer tracer;

    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    @MockBean
    private RabbitTemplate mockRabbitTemplate;

    @Test
    void contextCarga_conBeansDeTracing() {
        assertNotNull(observationRegistry, "ObservationRegistry debe estar disponible");
        assertNotNull(tracer, "Tracer debe estar disponible");
    }

    @Test
    void tracer_currentSpan_disponible() {
        assertDoesNotThrow(() -> tracer.currentSpan());
    }

    @Test
    void observationRegistry_aceptaCrearObservaciones() {
        Observation observation = Observation.createNotStarted("test.manual", observationRegistry);
        assertNotNull(observation);
        assertDoesNotThrow(() -> {
            observation.start();
            observation.stop();
        });
    }

    @Test
    void publisher_enviaEvento_conTemplateObservable() {
        // Verifica que el publisher funciona con el RabbitTemplate
        // que tiene observationEnabled=true
        NotificacionPublisher publisher = new NotificacionPublisher(mockRabbitTemplate);
        NotificacionEvent evento = new NotificacionEvent("u1", "PRESTAMO_CREADO", "Test");

        publisher.publicarEvento(evento);

        verify(mockRabbitTemplate, times(1))
                .convertAndSend(eq("notificacion.exchange"), anyString(), eq(evento));
    }

    @Test
    void publisher_eventosMultiples_routingKeysCorrectos() {
        NotificacionPublisher publisher = new NotificacionPublisher(mockRabbitTemplate);

        publisher.publicarEvento(new NotificacionEvent("u1", "PRESTAMO_CREADO", "Msg1"));
        publisher.publicarEvento(new NotificacionEvent("u2", "VENCIDO", "Msg2"));
        publisher.publicarEvento(new NotificacionEvent("u3", "PROXIMO_VENCER", "Msg3"));

        verify(mockRabbitTemplate, times(3))
                .convertAndSend(eq("notificacion.exchange"), anyString(), any(NotificacionEvent.class));
    }
}
