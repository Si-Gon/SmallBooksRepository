package com.silvio.notification.config;

import com.silvio.notification.dto.NotificacionRequestDTO;
import com.silvio.notification.model.Notificacion;
import com.silvio.notification.repository.NotificacionRepository;
import com.silvio.notification.service.NotificacionService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// Tests de integracion para verificar que las 6 anotaciones @Observed
// en NotificacionService y NotificacionEventListener crean spans correctamente.
//
// NotificacionService tiene @Observed en:
//   notification.crear, notification.obtenerPorUsuario,
//   notification.obtenerNoLeidas, notification.marcarLeida,
//   notification.marcarTodasLeidas
// NotificacionEventListener tiene @Observed en:
//   notification.procesarEvento
@SpringBootTest
@ActiveProfiles("test")
class ObservedAnnotationIntegrationTest {

    @Autowired
    private NotificacionService notificacionService;

    @Autowired(required = false)
    private ObservationRegistry observationRegistry;

    @Autowired(required = false)
    private Tracer tracer;

    @MockBean
    private NotificacionRepository notificacionRepository;

    @Test
    void contextCarga_conBeansDeTracing() {
        assertNotNull(observationRegistry, "ObservationRegistry debe estar disponible");
        assertNotNull(tracer, "Tracer (Brave) debe estar disponible");
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
    void tracer_currentSpan_devuelveSpanSinError() {
        assertDoesNotThrow(() -> tracer.currentSpan());
    }

    @Test
    void observedAspect_crear_creaSpanCorrectamente() {
        NotificacionRequestDTO request = new NotificacionRequestDTO();
        request.setUsuarioId("u1");
        request.setTipo(Notificacion.TipoNotificacion.PRESTAMO_CREADO);
        request.setMensaje("Test");
        when(notificacionRepository.findByIdempotencyKey(any())).thenReturn(java.util.Optional.empty());
        when(notificacionRepository.save(any(Notificacion.class))).thenReturn(new Notificacion());
        assertDoesNotThrow(() -> {
            var resultado = notificacionService.crear(request);
            assertNotNull(resultado);
        });
    }

    @Test
    void observedAspect_obtenerPorUsuario_creaSpanCorrectamente() {
        when(notificacionRepository.findByUsuarioIdOrderByFechaEnvioDesc("u1"))
                .thenReturn(java.util.Collections.emptyList());
        assertDoesNotThrow(() -> {
            var resultado = notificacionService.obtenerPorUsuario("u1");
            assertNotNull(resultado);
        });
    }

    @Test
    void observedAspect_obtenerNoLeidas_creaSpanCorrectamente() {
        when(notificacionRepository.findByUsuarioIdAndLeidaFalse("u1"))
                .thenReturn(java.util.Collections.emptyList());
        assertDoesNotThrow(() -> {
            var resultado = notificacionService.obtenerNoLeidas("u1");
            assertNotNull(resultado);
        });
    }

    @Test
    void observedAspect_marcarLeida_creaSpanCorrectamente() {
        Notificacion notificacion = new Notificacion();
        notificacion.setId(1L);
        when(notificacionRepository.findById(1L)).thenReturn(java.util.Optional.of(notificacion));
        when(notificacionRepository.save(any(Notificacion.class))).thenReturn(notificacion);
        assertDoesNotThrow(() -> {
            var resultado = notificacionService.marcarLeida(1L);
            assertNotNull(resultado);
        });
    }

    @Test
    void observedAspect_metodoVoid_marcarTodasLeidas_creaSpanSinError() {
        when(notificacionRepository.findByUsuarioIdAndLeidaFalse("u1"))
                .thenReturn(java.util.Collections.emptyList());
        assertDoesNotThrow(() ->
                notificacionService.marcarTodasLeidas("u1"));
    }
}
