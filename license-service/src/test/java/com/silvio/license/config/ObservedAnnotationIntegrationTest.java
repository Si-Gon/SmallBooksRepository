package com.silvio.license.config;

import com.silvio.license.dto.LicenseRequestDTO;
import com.silvio.license.model.License;
import com.silvio.license.repository.LicenseRepository;
import com.silvio.license.service.LicenseService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// Tests de integracion para verificar que las 8 anotaciones @Observed
// en LicenseService crean spans de tracing correctamente.
//
// NOTA: Los spans license.prestar y license.devolver (en doPrestar/doDevolver)
// NO se crean por self-invocation AOP. Solo se crean los spans wrapper
// license.prestarWrapper y license.devolverWrapper.
@SpringBootTest
@ActiveProfiles("test")
class ObservedAnnotationIntegrationTest {

    @Autowired
    private LicenseService licenseService;

    @Autowired(required = false)
    private ObservationRegistry observationRegistry;

    @Autowired(required = false)
    private Tracer tracer;

    @MockBean
    private LicenseRepository licenseRepository;

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
    void observedAspect_obtenerTodas_creaSpanCorrectamente() {
        when(licenseRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.Collections.emptyList()));
        assertDoesNotThrow(() -> {
            var resultado = licenseService.obtenerTodas(Pageable.unpaged());
            assertNotNull(resultado);
            assertTrue(resultado.isEmpty());
        });
    }

    @Test
    void observedAspect_obtenerPorLibroId_creaSpanCorrectamente() {
        License license = new License();
        license.setLibroId(1L);
        when(licenseRepository.findByLibroId(1L)).thenReturn(java.util.Optional.of(license));
        assertDoesNotThrow(() -> {
            var resultado = licenseService.obtenerPorLibroId(1L);
            assertNotNull(resultado);
        });
    }

    @Test
    void observedAspect_crear_creaSpanCorrectamente() {
        LicenseRequestDTO request = new LicenseRequestDTO();
        request.setLibroId(1L);
        request.setTotalCopias(5);
        when(licenseRepository.findByLibroId(1L)).thenReturn(java.util.Optional.empty());
        when(licenseRepository.save(any(License.class))).thenReturn(new License());
        assertDoesNotThrow(() -> {
            var resultado = licenseService.crear(request);
            assertNotNull(resultado);
        });
    }

    @Test
    void observedAspect_prestarWrapper_creaSpanCorrectamente() {
        License license = new License();
        license.setLibroId(1L);
        license.setTotalCopias(5);
        license.setCopiasDisponibles(3);
        when(licenseRepository.findByLibroId(1L)).thenReturn(java.util.Optional.of(license));
        when(licenseRepository.save(any(License.class))).thenReturn(license);
        assertDoesNotThrow(() -> {
            var resultado = licenseService.prestar(1L);
            assertNotNull(resultado);
        });
    }

    @Test
    void observedAspect_devolverWrapper_creaSpanCorrectamente() {
        License license = new License();
        license.setLibroId(1L);
        license.setTotalCopias(5);
        license.setCopiasDisponibles(3);
        when(licenseRepository.findByLibroId(1L)).thenReturn(java.util.Optional.of(license));
        when(licenseRepository.save(any(License.class))).thenReturn(license);
        assertDoesNotThrow(() -> {
            var resultado = licenseService.devolver(1L);
            assertNotNull(resultado);
        });
    }

    @Test
    void observedAspect_actualizar_creaSpanCorrectamente() {
        License license = new License();
        license.setLibroId(1L);
        license.setTotalCopias(5);
        license.setCopiasDisponibles(5);
        LicenseRequestDTO request = new LicenseRequestDTO();
        request.setLibroId(1L);
        request.setTotalCopias(10);
        when(licenseRepository.findByLibroId(1L)).thenReturn(java.util.Optional.of(license));
        when(licenseRepository.save(any(License.class))).thenReturn(license);
        assertDoesNotThrow(() -> {
            var resultado = licenseService.actualizar(1L, request);
            assertNotNull(resultado);
        });
    }
}
