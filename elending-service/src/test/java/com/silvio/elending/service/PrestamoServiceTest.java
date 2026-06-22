package com.silvio.elending.service;

import com.silvio.elending.client.LicenseClient;
import com.silvio.elending.client.NotificationClient;
import com.silvio.elending.client.SubscriptionClient;
import com.silvio.elending.dto.LicenciaDTO;
import com.silvio.elending.dto.PrestamoRequestDTO;
import com.silvio.elending.dto.PrestamoResponseDTO;
import com.silvio.elending.dto.SuscripcionDTO;
import com.silvio.elending.model.Prestamo;
import com.silvio.elending.model.Prestamo.EstadoPrestamo;
import com.silvio.elending.repository.PrestamoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrestamoServiceTest {

    @InjectMocks
    private PrestamoService prestamoService;

    @Mock
    private PrestamoRepository prestamoRepository;

    @Mock
    private LicenseClient licenseClient;

    @Mock
    private SubscriptionClient subscriptionClient;

    @Mock
    private NotificationClient notificationClient;

    // ─── helpers reutilizables ───────────────────────────────────────────────

    private SuscripcionDTO suscripcionBasico() {
        SuscripcionDTO s = new SuscripcionDTO();
        s.setPlan("BASICO");
        s.setMaxPrestamos(2);
        s.setDiasPrestamo(7);
        s.setActiva(true);
        return s;
    }

    private SuscripcionDTO suscripcionPremium() {
        SuscripcionDTO s = new SuscripcionDTO();
        s.setPlan("PREMIUM");
        s.setMaxPrestamos(5);
        s.setDiasPrestamo(14);
        s.setActiva(true);
        return s;
    }

    private LicenciaDTO licenciaDisponible() {
        LicenciaDTO l = new LicenciaDTO();
        l.setCopiasDisponibles(3);
        l.setTotalCopias(5);
        return l;
    }

    private LicenciaDTO licenciaSinCopias() {
        LicenciaDTO l = new LicenciaDTO();
        l.setCopiasDisponibles(0);
        l.setTotalCopias(5);
        return l;
    }

    private PrestamoRequestDTO request(Long libroId) {
        PrestamoRequestDTO r = new PrestamoRequestDTO();
        r.setLibroId(libroId);
        return r;
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ─── tests crearPrestamo ─────────────────────────────────────────────────

    @Test
    void crearPrestamo_exitoso_usuario_BASICO() {
        // Given
        String usuarioId = "usuario1";
        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(suscripcionBasico());
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(prestamoRepository.findByLibroIdAndEstado(1L, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(licenseClient.obtenerLicencia(1L)).thenReturn(licenciaDisponible());
        when(licenseClient.prestar(1L)).thenReturn(licenciaDisponible());
        when(prestamoRepository.save(any(Prestamo.class)))
                .thenAnswer(i -> i.getArgument(0));

        // When
        PrestamoResponseDTO resultado = prestamoService.crearPrestamo(request(1L), usuarioId);

        // Then
        assertNotNull(resultado);
        assertEquals(1L, resultado.getLibroId());
        assertEquals(usuarioId, resultado.getUsuarioId());
        assertEquals(EstadoPrestamo.ACTIVO, resultado.getEstado());
        assertNotNull(resultado.getFechaInicio());
        assertNotNull(resultado.getFechaVencimiento());
        // Plan BASICO: 7 días
        assertTrue(resultado.getFechaVencimiento().isAfter(resultado.getFechaInicio()));
        verify(licenseClient).prestar(1L);
        verify(prestamoRepository).save(any(Prestamo.class));
    }

    @Test
    void crearPrestamo_exitoso_usuario_PREMIUM() {
        // Given
        String usuarioId = "usuario2";
        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(suscripcionPremium());
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(prestamoRepository.findByLibroIdAndEstado(2L, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(licenseClient.obtenerLicencia(2L)).thenReturn(licenciaDisponible());
        when(licenseClient.prestar(2L)).thenReturn(licenciaDisponible());
        when(prestamoRepository.save(any(Prestamo.class)))
                .thenAnswer(i -> i.getArgument(0));

        // When
        PrestamoResponseDTO resultado = prestamoService.crearPrestamo(request(2L), usuarioId);

        // Then
        assertNotNull(resultado);
        assertEquals(2L, resultado.getLibroId());
        assertEquals(EstadoPrestamo.ACTIVO, resultado.getEstado());
        // Plan PREMIUM: 14 días — vencimiento debe ser posterior al de BASICO (7 días)
        assertTrue(resultado.getFechaVencimiento()
                .isAfter(resultado.getFechaInicio().plusDays(13)));
        verify(licenseClient).prestar(2L);
    }

    @Test
    void crearPrestamo_falla_usuario_BASICO_ya_tiene_2_prestamos_activos() {
        // Given
        String usuarioId = "usuario3";
        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(suscripcionBasico());
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(Arrays.asList(new Prestamo(), new Prestamo())); // límite alcanzado

        // When & Then
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> prestamoService.crearPrestamo(request(3L), usuarioId));

        assertTrue(ex.getMessage().contains("límite") || ex.getMessage().contains("BASICO")
                || ex.getMessage().contains("2"));
        // Verificar que NO intentó crear el préstamo
        verify(prestamoRepository, never()).save(any(Prestamo.class));
        verify(licenseClient, never()).prestar(anyLong());
    }

    @Test
    void crearPrestamo_falla_usuario_PREMIUM_ya_tiene_5_prestamos_activos() {
        // Given
        String usuarioId = "usuario4";
        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(suscripcionPremium());
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(Arrays.asList(
                        new Prestamo(), new Prestamo(), new Prestamo(),
                        new Prestamo(), new Prestamo())); // 5 = límite PREMIUM

        // When & Then
        assertThrows(RuntimeException.class,
                () -> prestamoService.crearPrestamo(request(4L), usuarioId));
        verify(prestamoRepository, never()).save(any(Prestamo.class));
    }

    @Test
    void crearPrestamo_falla_cuando_no_hay_copias_disponibles() {
        // Given
        String usuarioId = "usuario5";
        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(suscripcionBasico());
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(prestamoRepository.findByLibroIdAndEstado(5L, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(licenseClient.obtenerLicencia(5L)).thenReturn(licenciaSinCopias());

        // When & Then
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> prestamoService.crearPrestamo(request(5L), usuarioId));

        assertTrue(ex.getMessage().contains("copias") || ex.getMessage().contains("disponibles"));
        verify(licenseClient, never()).prestar(anyLong());
        verify(prestamoRepository, never()).save(any(Prestamo.class));
    }

    @Test
    void crearPrestamo_falla_cuando_usuario_ya_tiene_este_libro() {
        // Given
        String usuarioId = "usuario6";
        Prestamo prestamoExistente = new Prestamo();
        prestamoExistente.setUsuarioId(usuarioId);
        prestamoExistente.setLibroId(6L);

        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(suscripcionBasico());
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(prestamoRepository.findByLibroIdAndEstado(6L, EstadoPrestamo.ACTIVO))
                .thenReturn(Arrays.asList(prestamoExistente)); // ya tiene este libro

        // When & Then
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> prestamoService.crearPrestamo(request(6L), usuarioId));

        assertTrue(ex.getMessage().contains("Ya tienes"));
        verify(licenseClient, never()).prestar(anyLong());
    }

    @Test
    void crearPrestamo_aplica_plan_BASICO_por_defecto_si_falla_subscription() {
        // Given
        String usuarioId = "usuario7";
        when(subscriptionClient.obtenerSuscripcion(usuarioId))
                .thenThrow(new RuntimeException("Subscription service no disponible"));
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(prestamoRepository.findByLibroIdAndEstado(7L, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(licenseClient.obtenerLicencia(7L)).thenReturn(licenciaDisponible());
        when(licenseClient.prestar(7L)).thenReturn(licenciaDisponible());
        when(prestamoRepository.save(any(Prestamo.class)))
                .thenAnswer(i -> i.getArgument(0));

        // When — no debe lanzar excepción, aplica BASICO por defecto
        PrestamoResponseDTO resultado = prestamoService.crearPrestamo(request(7L), usuarioId);

        // Then
        assertNotNull(resultado);
        // Con plan BASICO por defecto: 7 días de vencimiento
        assertTrue(resultado.getFechaVencimiento().isAfter(resultado.getFechaInicio()));
        verify(prestamoRepository).save(any(Prestamo.class));
    }

    // ─── tests obtenerPrestamosActivos ───────────────────────────────────────

    @Test
    void obtenerPrestamosActivos_devuelve_lista_correcta() {
        // Given
        String usuarioId = "usuario8";
        Prestamo p1 = new Prestamo();
        p1.setUsuarioId(usuarioId);
        p1.setLibroId(1L);
        p1.setEstado(EstadoPrestamo.ACTIVO);

        Prestamo p2 = new Prestamo();
        p2.setUsuarioId(usuarioId);
        p2.setLibroId(2L);
        p2.setEstado(EstadoPrestamo.ACTIVO);

        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(Arrays.asList(p1, p2));

        // When
        List<PrestamoResponseDTO> resultado = prestamoService.obtenerPrestamosActivos(usuarioId);

        // Then
        assertNotNull(resultado);
        assertEquals(2, resultado.size());
        assertEquals(EstadoPrestamo.ACTIVO, resultado.get(0).getEstado());
        verify(prestamoRepository).findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO);
    }

    @Test
    void obtenerPrestamosActivos_devuelve_lista_vacia_si_no_hay_prestamos() {
        // Given
        String usuarioId = "usuario9";
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());

        // When
        List<PrestamoResponseDTO> resultado = prestamoService.obtenerPrestamosActivos(usuarioId);

        // Then
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }

    // ─── tests obtenerHistorial ──────────────────────────────────────────────

    @Test
    void obtenerHistorial_devuelve_todos_los_prestamos_del_usuario() {
        // Given
        String usuarioId = "usuario10";
        Prestamo activo = new Prestamo();
        activo.setUsuarioId(usuarioId);
        activo.setEstado(EstadoPrestamo.ACTIVO);

        Prestamo vencido = new Prestamo();
        vencido.setUsuarioId(usuarioId);
        vencido.setEstado(EstadoPrestamo.VENCIDO);

        when(prestamoRepository.findByUsuarioId(usuarioId))
                .thenReturn(Arrays.asList(activo, vencido));

        // When
        List<PrestamoResponseDTO> resultado = prestamoService.obtenerHistorial(usuarioId);

        // Then
        assertNotNull(resultado);
        assertEquals(2, resultado.size());
        verify(prestamoRepository).findByUsuarioId(usuarioId);
    }
}