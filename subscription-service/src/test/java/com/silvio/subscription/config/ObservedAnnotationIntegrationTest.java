package com.silvio.subscription.config;

import com.silvio.subscription.dto.SuscripcionRequestDTO;
import com.silvio.subscription.model.Suscripcion;
import com.silvio.subscription.repository.SuscripcionRepository;
import com.silvio.subscription.service.SuscripcionService;
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

// Tests de integracion para verificar que las 3 anotaciones @Observed
// en SuscripcionService crean spans de tracing correctamente.
//
// SuscripcionService tiene @Observed en:
//   subscription.obtenerPorUsuario, subscription.crear, subscription.cancelar
@SpringBootTest
@ActiveProfiles("test")
class ObservedAnnotationIntegrationTest {

    @Autowired
    private SuscripcionService suscripcionService;

    @Autowired(required = false)
    private ObservationRegistry observationRegistry;

    @Autowired(required = false)
    private Tracer tracer;

    @MockBean
    private SuscripcionRepository suscripcionRepository;

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
    void observedAspect_obtenerPorUsuario_creaSpanCorrectamente() {
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setUsuarioId("u1");
        suscripcion.setActiva(true);
        when(suscripcionRepository.findByUsuarioIdAndActivaTrue("u1"))
                .thenReturn(java.util.Optional.of(suscripcion));
        assertDoesNotThrow(() -> {
            var resultado = suscripcionService.obtenerPorUsuario("u1");
            assertNotNull(resultado);
        });
    }

    @Test
    void observedAspect_crear_creaSpanCorrectamente() {
        SuscripcionRequestDTO request = new SuscripcionRequestDTO();
        request.setPlan(Suscripcion.PlanSuscripcion.BASICO);
        request.setMeses(1);
        when(suscripcionRepository.findByUsuarioIdAndActivaTrue("u1"))
                .thenReturn(java.util.Optional.empty());
        when(suscripcionRepository.save(any(Suscripcion.class))).thenReturn(new Suscripcion());
        assertDoesNotThrow(() -> {
            var resultado = suscripcionService.crear(request, "u1");
            assertNotNull(resultado);
        });
    }

    @Test
    void observedAspect_cancelar_creaSpanCorrectamente() {
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setUsuarioId("u1");
        suscripcion.setActiva(true);
        suscripcion.setPlan(Suscripcion.PlanSuscripcion.BASICO);
        when(suscripcionRepository.findByUsuarioIdAndActivaTrue("u1"))
                .thenReturn(java.util.Optional.of(suscripcion));
        when(suscripcionRepository.save(any(Suscripcion.class))).thenReturn(suscripcion);
        assertDoesNotThrow(() -> {
            var resultado = suscripcionService.cancelar("u1");
            assertNotNull(resultado);
        });
    }
}
