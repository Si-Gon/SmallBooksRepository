package com.silvio.subscription.service;

import com.silvio.subscription.dto.SuscripcionRequestDTO;
import com.silvio.subscription.dto.SuscripcionResponseDTO;
import com.silvio.subscription.model.Suscripcion;
import com.silvio.subscription.model.Suscripcion.PlanSuscripcion;
import com.silvio.subscription.repository.SuscripcionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de SuscripcionService.
 *
 * La lógica más importante del servicio está en:
 * 1. mapearADto() — asigna maxPrestamos y diasPrestamo según el plan (BASICO vs PREMIUM)
 * 2. crear() — cancela la suscripción anterior antes de crear la nueva
 * 3. obtenerPorUsuario() / cancelar() — lanzan excepción si no hay suscripción activa
 *
 * ArgumentCaptor: herramienta de Mockito que "captura" el argumento real que
 * se le pasó a un mock, permitiéndonos inspeccionarlo después en el test.
 * Útil para verificar que el objeto Suscripcion que se guardó tiene los campos correctos.
 */
@ExtendWith(MockitoExtension.class)
class SuscripcionServiceTest {

    @Mock
    private SuscripcionRepository suscripcionRepository;

    @InjectMocks
    private SuscripcionService suscripcionService;

    private Suscripcion suscripcionEntity(String usuarioId, PlanSuscripcion plan) {
        Suscripcion s = new Suscripcion();
        s.setId(1L);
        s.setUsuarioId(usuarioId);
        s.setPlan(plan);
        s.setFechaInicio(LocalDateTime.now().minusMonths(1));
        s.setFechaFin(LocalDateTime.now().plusMonths(1));
        s.setActiva(true);
        return s;
    }

    private SuscripcionRequestDTO request(PlanSuscripcion plan, int meses) {
        SuscripcionRequestDTO r = new SuscripcionRequestDTO();
        r.setPlan(plan);
        r.setMeses(meses);
        return r;
    }

    // =====================================================================
    // obtenerPorUsuario()
    // =====================================================================

    @Test
    void obtenerPorUsuario_conSuscripcionActiva_retornaDTO() {
        Suscripcion s = suscripcionEntity("silvio", PlanSuscripcion.BASICO);
        when(suscripcionRepository.findByUsuarioIdAndActivaTrue("silvio"))
            .thenReturn(Optional.of(s));

        SuscripcionResponseDTO resultado = suscripcionService.obtenerPorUsuario("silvio");

        assertThat(resultado.getUsuarioId()).isEqualTo("silvio");
        assertThat(resultado.getPlan()).isEqualTo(PlanSuscripcion.BASICO);
        assertThat(resultado.getActiva()).isTrue();
        // Verifica que el mapeo de límites para BASICO es correcto
        assertThat(resultado.getMaxPrestamos()).isEqualTo(2);
        assertThat(resultado.getDiasPrestamo()).isEqualTo(7);
    }

    // =====================================================================
    // crear() — plan BASICO
    // =====================================================================

    @Test
    void crear_planBasico_guardaYRetornaDtoConLimitesCorrectos() {
        when(suscripcionRepository.findByUsuarioIdAndActivaTrue("silvio"))
            .thenReturn(Optional.empty()); // sin suscripción previa

        // El save devuelve la misma entidad pero con id asignado
        when(suscripcionRepository.save(any(Suscripcion.class))).thenAnswer(inv -> {
            Suscripcion s = inv.getArgument(0);
            s.setId(10L);
            return s;
        });

        SuscripcionResponseDTO resultado = suscripcionService.crear(
            request(PlanSuscripcion.BASICO, 1), "silvio");

        assertThat(resultado.getUsuarioId()).isEqualTo("silvio");
        assertThat(resultado.getPlan()).isEqualTo(PlanSuscripcion.BASICO);
        assertThat(resultado.getActiva()).isTrue();
        assertThat(resultado.getMaxPrestamos()).isEqualTo(2);   // BASICO = 2 préstamos
        assertThat(resultado.getDiasPrestamo()).isEqualTo(7);    // BASICO = 7 días
    }

    // =====================================================================
    // crear() — plan PREMIUM
    // =====================================================================

    @Test
    void crear_planPremium_retornaDtoConLimitesPremium() {
        when(suscripcionRepository.findByUsuarioIdAndActivaTrue("silvio"))
            .thenReturn(Optional.empty());

        when(suscripcionRepository.save(any(Suscripcion.class))).thenAnswer(inv -> {
            Suscripcion s = inv.getArgument(0);
            s.setId(11L);
            return s;
        });

        SuscripcionResponseDTO resultado = suscripcionService.crear(
            request(PlanSuscripcion.PREMIUM, 3), "silvio");

        // PREMIUM tiene límites superiores
        assertThat(resultado.getMaxPrestamos()).isEqualTo(5);
        assertThat(resultado.getDiasPrestamo()).isEqualTo(14);
        // Verifica que la fecha de fin es ~3 meses después del inicio
        assertThat(resultado.getFechaFin())
            .isAfter(LocalDateTime.now().plusMonths(2));
    }

    // =====================================================================
    // crear() — cancela suscripción anterior si existe
    // =====================================================================

    @Test
    void crear_conSuscripcionAnterior_cancelaAntesDeCrear() {
        // El usuario ya tiene BASICO activo
        Suscripcion anterior = suscripcionEntity("silvio", PlanSuscripcion.BASICO);
        when(suscripcionRepository.findByUsuarioIdAndActivaTrue("silvio"))
            .thenReturn(Optional.of(anterior));

        when(suscripcionRepository.save(any(Suscripcion.class))).thenAnswer(inv -> {
            Suscripcion s = inv.getArgument(0);
            s.setId(12L);
            return s;
        });

        // Crea PREMIUM encima del BASICO
        suscripcionService.crear(request(PlanSuscripcion.PREMIUM, 1), "silvio");

        // ArgumentCaptor captura todos los objetos que se pasaron a save()
        // El primero debe ser la suscripción anterior con activa=false
        // El segundo debe ser la nueva suscripción con activa=true
        ArgumentCaptor<Suscripcion> captor = ArgumentCaptor.forClass(Suscripcion.class);
        verify(suscripcionRepository, times(2)).save(captor.capture());

        Suscripcion guardadaAnterior = captor.getAllValues().get(0);
        Suscripcion guardadaNueva   = captor.getAllValues().get(1);

        assertThat(guardadaAnterior.getActiva()).isFalse();  // La anterior se canceló
        assertThat(guardadaNueva.getActiva()).isTrue();       // La nueva está activa
        assertThat(guardadaNueva.getPlan()).isEqualTo(PlanSuscripcion.PREMIUM);
    }

    // =====================================================================
    // cancelar()
    // =====================================================================

    @Test
    void cancelar_suscripcionActiva_setActivaFalseYGuarda() {
        Suscripcion s = suscripcionEntity("silvio", PlanSuscripcion.PREMIUM);
        when(suscripcionRepository.findByUsuarioIdAndActivaTrue("silvio"))
            .thenReturn(Optional.of(s));
        when(suscripcionRepository.save(any())).thenReturn(s);

        SuscripcionResponseDTO resultado = suscripcionService.cancelar("silvio");

        // El service pone activa=false antes de guardar
        assertThat(s.getActiva()).isFalse();
        verify(suscripcionRepository).save(s);
        // El DTO mapeado también debe reflejar activa=false
        assertThat(resultado.getActiva()).isFalse();
    }

    // =====================================================================
    // mapearADto() — rama BASICO vs PREMIUM (cubierta indirectamente arriba,
    // pero aquí lo hacemos explícito para que el reporte JaCoCo lo muestre)
    // =====================================================================

    @Test
    void mapeo_planBasico_asignaLimitesBasico() {
        Suscripcion s = suscripcionEntity("test", PlanSuscripcion.BASICO);
        when(suscripcionRepository.findByUsuarioIdAndActivaTrue("test"))
            .thenReturn(Optional.of(s));

        SuscripcionResponseDTO dto = suscripcionService.obtenerPorUsuario("test");

        assertThat(dto.getMaxPrestamos()).isEqualTo(2);
        assertThat(dto.getDiasPrestamo()).isEqualTo(7);
    }

    @Test
    void mapeo_planPremium_asignaLimitesPremium() {
        Suscripcion s = suscripcionEntity("test", PlanSuscripcion.PREMIUM);
        when(suscripcionRepository.findByUsuarioIdAndActivaTrue("test"))
            .thenReturn(Optional.of(s));

        SuscripcionResponseDTO dto = suscripcionService.obtenerPorUsuario("test");

        assertThat(dto.getMaxPrestamos()).isEqualTo(5);
        assertThat(dto.getDiasPrestamo()).isEqualTo(14);
    }
}
