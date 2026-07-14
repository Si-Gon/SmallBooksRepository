package com.silvio.elending.service;

import com.silvio.elending.client.LicenseClient;
import com.silvio.elending.client.SubscriptionClient;
import com.silvio.elending.dto.LicenciaDTO;
import com.silvio.elending.dto.PrestamoRequestDTO;
import com.silvio.elending.messaging.NotificacionEvent;
import com.silvio.elending.messaging.NotificacionPublisher;
import com.silvio.elending.dto.PrestamoResponseDTO;
import com.silvio.elending.dto.SuscripcionDTO;
import com.silvio.elending.model.Prestamo;
import com.silvio.elending.model.Prestamo.EstadoPrestamo;
import com.silvio.elending.exception.CopiaNoDisponibleException;
import com.silvio.elending.exception.ErrorCreacionPrestamoException;
import com.silvio.elending.exception.LimitePrestamosExcedidoException;
import com.silvio.elending.exception.PrestamoDuplicadoException;
import com.silvio.elending.exception.UltimaCopiaNoDisponibleException;
import com.silvio.elending.repository.PrestamoRepository;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
    private NotificacionPublisher notificacionPublisher;

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
        LimitePrestamosExcedidoException ex = assertThrows(LimitePrestamosExcedidoException.class,
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
        assertThrows(LimitePrestamosExcedidoException.class,
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
        CopiaNoDisponibleException ex = assertThrows(CopiaNoDisponibleException.class,
                () -> prestamoService.crearPrestamo(request(5L), usuarioId));

        assertTrue(ex.getMessage().contains("copias") || ex.getMessage().contains("disponibles"));
        verify(licenseClient, never()).prestar(anyLong());
        verify(prestamoRepository, never()).save(any(Prestamo.class));
    }

    @Test
    void crearPrestamo_copiasDisponibles_null_lanza_excepcion() {
        // Given — copiasDisponibles = null (ej: licencia recién creada sin stock inicializado)
        String usuarioId = "usuario5b";
        LicenciaDTO licenciaSinStock = new LicenciaDTO();
        licenciaSinStock.setTotalCopias(5);
        licenciaSinStock.setCopiasDisponibles(null);

        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(suscripcionBasico());
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(prestamoRepository.findByLibroIdAndEstado(55L, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(licenseClient.obtenerLicencia(55L)).thenReturn(licenciaSinStock);

        // When & Then
        CopiaNoDisponibleException ex = assertThrows(CopiaNoDisponibleException.class,
                () -> prestamoService.crearPrestamo(request(55L), usuarioId));

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
        PrestamoDuplicadoException ex = assertThrows(PrestamoDuplicadoException.class,
                () -> prestamoService.crearPrestamo(request(6L), usuarioId));

        assertTrue(ex.getMessage().contains("Ya tienes"));
        verify(licenseClient, never()).prestar(anyLong());
    }

    @Test
    void crearPrestamo_con_1_activo_de_2_continua_exitosamente() {
        // Given — usuario con 1 préstamo activo de 2 permitidos (BASICO), aún puede pedir otro
        String usuarioId = "usuario6b";
        Prestamo prestamoExistente = new Prestamo();
        prestamoExistente.setUsuarioId(usuarioId);
        prestamoExistente.setLibroId(99L);
        prestamoExistente.setEstado(EstadoPrestamo.ACTIVO);

        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(suscripcionBasico());
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(Arrays.asList(prestamoExistente)); // 1 activo
        when(prestamoRepository.findByLibroIdAndEstado(66L, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(licenseClient.obtenerLicencia(66L)).thenReturn(licenciaDisponible());
        when(licenseClient.prestar(66L)).thenReturn(licenciaDisponible());
        when(prestamoRepository.save(any(Prestamo.class)))
                .thenAnswer(i -> i.getArgument(0));

        // When
        PrestamoResponseDTO resultado = prestamoService.crearPrestamo(request(66L), usuarioId);

        // Then — el préstamo se crea exitosamente
        assertNotNull(resultado);
        assertEquals(66L, resultado.getLibroId());
        assertEquals(EstadoPrestamo.ACTIVO, resultado.getEstado());
        verify(licenseClient).prestar(66L);
        verify(prestamoRepository).save(any(Prestamo.class));
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

    // ─── tests crearPrestamo — nulls en SuscripcionDTO ──────────────────────

    @Test
    void crearPrestamo_cuandoMaxPrestamosEsNull_aplicaFallback() {
        // Given — SuscripcionDTO con maxPrestamos = null
        // El fallback asigna 2 (BASICO) para evitar NPE por auto-unboxing
        String usuarioId = "usuario_null_max";
        SuscripcionDTO suscripcionSinMax = new SuscripcionDTO();
        suscripcionSinMax.setPlan("BASICO");
        suscripcionSinMax.setMaxPrestamos(null);  // ← null
        suscripcionSinMax.setDiasPrestamo(7);
        suscripcionSinMax.setActiva(true);

        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(suscripcionSinMax);
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(prestamoRepository.findByLibroIdAndEstado(111L, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(licenseClient.obtenerLicencia(111L)).thenReturn(licenciaDisponible());
        when(licenseClient.prestar(111L)).thenReturn(licenciaDisponible());
        when(prestamoRepository.save(any(Prestamo.class))).thenAnswer(i -> i.getArgument(0));

        // When — no debe lanzar excepción, aplica fallback a BASICO
        PrestamoResponseDTO resultado = prestamoService.crearPrestamo(request(111L), usuarioId);

        // Then
        assertNotNull(resultado);
        assertEquals(111L, resultado.getLibroId());
        assertEquals(EstadoPrestamo.ACTIVO, resultado.getEstado());
        verify(prestamoRepository).save(any(Prestamo.class));
        verify(licenseClient).prestar(111L);
    }

    @Test
    void crearPrestamo_cuandoDiasPrestamoEsNull_aplicaFallback() {
        // Given — SuscripcionDTO con diasPrestamo = null
        // El fallback asigna 7 (BASICO) para evitar NPE por auto-unboxing en plusDays()
        String usuarioId = "usuario_null_dias";
        SuscripcionDTO suscripcionSinDias = new SuscripcionDTO();
        suscripcionSinDias.setPlan("BASICO");
        suscripcionSinDias.setMaxPrestamos(2);
        suscripcionSinDias.setDiasPrestamo(null);  // ← null
        suscripcionSinDias.setActiva(true);

        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(suscripcionSinDias);
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(prestamoRepository.findByLibroIdAndEstado(112L, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(licenseClient.obtenerLicencia(112L)).thenReturn(licenciaDisponible());
        when(licenseClient.prestar(112L)).thenReturn(licenciaDisponible());
        when(prestamoRepository.save(any(Prestamo.class))).thenAnswer(i -> i.getArgument(0));

        // When — no debe lanzar excepción, aplica fallback a BASICO
        PrestamoResponseDTO resultado = prestamoService.crearPrestamo(request(112L), usuarioId);

        // Then
        assertNotNull(resultado);
        assertEquals(112L, resultado.getLibroId());
        assertEquals(EstadoPrestamo.ACTIVO, resultado.getEstado());
        // Con fallback: 7 días de vencimiento
        assertTrue(resultado.getFechaVencimiento()
                .isAfter(resultado.getFechaInicio().plusDays(6)));
        verify(prestamoRepository).save(any(Prestamo.class));
        verify(licenseClient).prestar(112L);
    }

    // ─── tests crearPrestamo — verificación exacta de duración ──────────────

    @Test
    void crearPrestamo_verificaDuracionExacta_BASICO() {
        // Given — plan BASICO = 7 días de préstamo
        String usuarioId = "usuario_dur_basico";
        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(suscripcionBasico());
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(prestamoRepository.findByLibroIdAndEstado(113L, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(licenseClient.obtenerLicencia(113L)).thenReturn(licenciaDisponible());
        when(licenseClient.prestar(113L)).thenReturn(licenciaDisponible());
        when(prestamoRepository.save(any(Prestamo.class)))
                .thenAnswer(i -> i.getArgument(0));

        // When
        PrestamoResponseDTO resultado = prestamoService.crearPrestamo(request(113L), usuarioId);

        // Then — exactamente 7 días después (con precisión de segundos)
        assertNotNull(resultado);
        assertEquals(
                resultado.getFechaInicio().plusDays(7),
                resultado.getFechaVencimiento());
        verify(prestamoRepository).save(any(Prestamo.class));
    }

    @Test
    void crearPrestamo_verificaDuracionExacta_PREMIUM() {
        // Given — plan PREMIUM = 14 días de préstamo
        String usuarioId = "usuario_dur_premium";
        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(suscripcionPremium());
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(prestamoRepository.findByLibroIdAndEstado(114L, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(licenseClient.obtenerLicencia(114L)).thenReturn(licenciaDisponible());
        when(licenseClient.prestar(114L)).thenReturn(licenciaDisponible());
        when(prestamoRepository.save(any(Prestamo.class)))
                .thenAnswer(i -> i.getArgument(0));

        // When
        PrestamoResponseDTO resultado = prestamoService.crearPrestamo(request(114L), usuarioId);

        // Then — exactamente 14 días después
        assertNotNull(resultado);
        assertEquals(
                resultado.getFechaInicio().plusDays(14),
                resultado.getFechaVencimiento());
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
    void obtenerPrestamosActivos_devuelve_vacio_cuando_usuario_solo_tiene_vencidos() {
        // Given — usuario tiene solo préstamos VENCIDO, el método filtra por ACTIVO
        String usuarioId = "usuario8b";
        Prestamo vencido = new Prestamo();
        vencido.setUsuarioId(usuarioId);
        vencido.setLibroId(77L);
        vencido.setEstado(EstadoPrestamo.VENCIDO);

        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>()); // 0 activos

        // When
        List<PrestamoResponseDTO> resultado = prestamoService.obtenerPrestamosActivos(usuarioId);

        // Then
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
        verify(prestamoRepository).findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO);
        verify(prestamoRepository, never()).findByUsuarioId(anyString());
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

    @Test
    void obtenerHistorial_devuelve_lista_vacia_si_no_hay_prestamos() {
        // Given
        String usuarioId = "usuario11";
        when(prestamoRepository.findByUsuarioId(usuarioId))
                .thenReturn(new ArrayList<>());

        // When
        List<PrestamoResponseDTO> resultado = prestamoService.obtenerHistorial(usuarioId);

        // Then
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
        verify(prestamoRepository).findByUsuarioId(usuarioId);
    }

    // ─── tests obtenerTodos ──────────────────────────────────────────────────

    @Test
    void obtenerTodos_devuelve_todos_los_prestamos() {
        // Given
        Prestamo p1 = new Prestamo();
        p1.setId(1L);
        p1.setUsuarioId("u1");
        p1.setLibroId(1L);
        p1.setEstado(EstadoPrestamo.ACTIVO);

        Prestamo p2 = new Prestamo();
        p2.setId(2L);
        p2.setUsuarioId("u2");
        p2.setLibroId(2L);
        p2.setEstado(EstadoPrestamo.VENCIDO);

        when(prestamoRepository.findAll()).thenReturn(Arrays.asList(p1, p2));

        // When
        List<PrestamoResponseDTO> resultado = prestamoService.obtenerTodos();

        // Then
        assertNotNull(resultado);
        assertEquals(2, resultado.size());
        verify(prestamoRepository).findAll();
    }

    @Test
    void obtenerTodos_devuelve_lista_vacia_si_no_hay_prestamos() {
        // Given
        when(prestamoRepository.findAll()).thenReturn(new ArrayList<>());

        // When
        List<PrestamoResponseDTO> resultado = prestamoService.obtenerTodos();

        // Then
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
        verify(prestamoRepository).findAll();
    }

    // ─── tests crearPrestamo — errores en Feign clients ──────────────────────

    @Test
    void crearPrestamo_falla_cuando_licenseClient_obtenerLicencia_lanza_excepcion() {
        // Given
        String usuarioId = "usuario12";
        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(suscripcionBasico());
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(prestamoRepository.findByLibroIdAndEstado(12L, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(licenseClient.obtenerLicencia(12L))
                .thenThrow(new RuntimeException("License service no disponible"));

        // When & Then — la excepción se propaga directamente sin envolver
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> prestamoService.crearPrestamo(request(12L), usuarioId));

        assertTrue(ex.getMessage().contains("License service no disponible"));
        verify(licenseClient, never()).prestar(anyLong());
        verify(prestamoRepository, never()).save(any(Prestamo.class));
    }

    @Test
    void crearPrestamo_falla_cuando_licenseClient_prestar_lanza_excepcion() {
        // Given
        String usuarioId = "usuario13";
        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(suscripcionBasico());
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(prestamoRepository.findByLibroIdAndEstado(13L, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(licenseClient.obtenerLicencia(13L)).thenReturn(licenciaDisponible());
        when(licenseClient.prestar(13L))
                .thenThrow(new RuntimeException("License service error al prestar"));

        // When & Then — la excepción se propaga directamente sin envolver
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> prestamoService.crearPrestamo(request(13L), usuarioId));

        assertTrue(ex.getMessage().contains("License service error al prestar"));
        verify(prestamoRepository, never()).save(any(Prestamo.class));
    }

    @Test
    void crearPrestamo_compensa_cuando_save_falla() {
        // Given
        String usuarioId = "usuario14";
        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(suscripcionBasico());
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(prestamoRepository.findByLibroIdAndEstado(14L, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(licenseClient.obtenerLicencia(14L)).thenReturn(licenciaDisponible());
        when(licenseClient.prestar(14L)).thenReturn(licenciaDisponible());
        when(prestamoRepository.save(any(Prestamo.class)))
                .thenThrow(new RuntimeException("Error de base de datos"));

        // When & Then
        ErrorCreacionPrestamoException ex = assertThrows(ErrorCreacionPrestamoException.class,
                () -> prestamoService.crearPrestamo(request(14L), usuarioId));

        assertTrue(ex.getMessage().contains("revertida"));
        // Verificar compensación: devolver la copia
        verify(licenseClient).prestar(14L);
        verify(licenseClient).devolver(14L);
        verify(prestamoRepository).save(any(Prestamo.class));
    }

    @Test
    void crearPrestamo_compensa_y_logea_cuando_save_y_compensacion_fallan() {
        // Given
        String usuarioId = "usuario15";
        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(suscripcionBasico());
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(prestamoRepository.findByLibroIdAndEstado(15L, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(licenseClient.obtenerLicencia(15L)).thenReturn(licenciaDisponible());
        when(licenseClient.prestar(15L)).thenReturn(licenciaDisponible());
        when(prestamoRepository.save(any(Prestamo.class)))
                .thenThrow(new RuntimeException("Error de base de datos"));
        // La compensación también falla
        when(licenseClient.devolver(15L))
                .thenThrow(new RuntimeException("License service no disponible para compensar"));

        // When & Then
        ErrorCreacionPrestamoException ex = assertThrows(ErrorCreacionPrestamoException.class,
                () -> prestamoService.crearPrestamo(request(15L), usuarioId));

        assertTrue(ex.getMessage().contains("revertida"));
        verify(licenseClient).prestar(15L);
        verify(licenseClient).devolver(15L); // compensación intentada
        verify(prestamoRepository).save(any(Prestamo.class));
    }

    @Test
    void crearPrestamo_notificacion_falla_no_rompe_flujo() {
        // Given
        String usuarioId = "usuario16";
        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(suscripcionBasico());
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(prestamoRepository.findByLibroIdAndEstado(16L, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(licenseClient.obtenerLicencia(16L)).thenReturn(licenciaDisponible());
        when(licenseClient.prestar(16L)).thenReturn(licenciaDisponible());
        when(prestamoRepository.save(any(Prestamo.class)))
                .thenAnswer(i -> i.getArgument(0));
        // Notificación falla — no debe romper el flujo
        doThrow(new RuntimeException("RabbitMQ no disponible"))
                .when(notificacionPublisher).publicarEvento(any(NotificacionEvent.class));

        // When
        PrestamoResponseDTO resultado = prestamoService.crearPrestamo(request(16L), usuarioId);

        // Then — el préstamo se crea a pesar del error de notificación
        assertNotNull(resultado);
        assertEquals(16L, resultado.getLibroId());
        assertEquals(EstadoPrestamo.ACTIVO, resultado.getEstado());
        verify(licenseClient).prestar(16L);
        verify(prestamoRepository).save(any(Prestamo.class));
        verify(notificacionPublisher).publicarEvento(any(NotificacionEvent.class));
    }

    // ─── tests cerrarPrestamosVencidos (scheduler) ───────────────────────────

    @Test
    void cerrarPrestamosVencidos_cierra_prestamos_vencidos_y_notifica() {
        // Given
        Prestamo vencido = new Prestamo();
        vencido.setId(1L);
        vencido.setUsuarioId("usuario17");
        vencido.setLibroId(10L);
        vencido.setEstado(EstadoPrestamo.ACTIVO);
        vencido.setFechaVencimiento(LocalDateTime.now().minusDays(1));

        when(prestamoRepository.findByEstadoAndFechaVencimientoBefore(
                eq(EstadoPrestamo.ACTIVO), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(vencido));
        when(licenseClient.devolver(10L)).thenReturn(licenciaDisponible());
        when(prestamoRepository.save(any(Prestamo.class)))
                .thenAnswer(i -> i.getArgument(0));

        // When
        prestamoService.cerrarPrestamosVencidos();

        // Then
        verify(licenseClient).devolver(10L);
        verify(prestamoRepository).save(any(Prestamo.class));
        assertEquals(EstadoPrestamo.VENCIDO, vencido.getEstado());
        verify(notificacionPublisher).publicarEvento(any(NotificacionEvent.class));
    }

    @Test
    void cerrarPrestamosVencidos_con_vencidos_y_proximos_en_misma_ejecucion() {
        // Given — un préstamo vencido y uno próximo a vencer en la misma ejecución
        Prestamo vencido = new Prestamo();
        vencido.setId(1L);
        vencido.setUsuarioId("usuario17b");
        vencido.setLibroId(10L);
        vencido.setEstado(EstadoPrestamo.ACTIVO);
        vencido.setFechaVencimiento(LocalDateTime.now().minusDays(1));

        Prestamo proximo = new Prestamo();
        proximo.setId(2L);
        proximo.setUsuarioId("usuario18b");
        proximo.setLibroId(20L);
        proximo.setEstado(EstadoPrestamo.ACTIVO);
        proximo.setFechaVencimiento(LocalDateTime.now().plusDays(1));

        // Primera llamada (vencidos) → devuelve vencido
        // Segunda llamada (próximos) → devuelve vencido + proximo (el filter() excluye el vencido)
        when(prestamoRepository.findByEstadoAndFechaVencimientoBefore(
                eq(EstadoPrestamo.ACTIVO), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(vencido))    // 1ra: vencidos (ahora)
                .thenReturn(Arrays.asList(vencido, proximo)); // 2da: próximos (ahora + 2d)

        when(licenseClient.devolver(10L)).thenReturn(licenciaDisponible());
        when(prestamoRepository.save(any(Prestamo.class)))
                .thenAnswer(i -> i.getArgument(0));

        // When
        prestamoService.cerrarPrestamosVencidos();

        // Then — vencido se cerró y se notificó, próximo también se notificó
        verify(licenseClient).devolver(10L);
        verify(licenseClient, never()).devolver(20L);
        verify(prestamoRepository).save(any(Prestamo.class));
        assertEquals(EstadoPrestamo.VENCIDO, vencido.getEstado());
        // Se notificó: vencimiento + próximo a vencer
        verify(notificacionPublisher, times(2)).publicarEvento(any(NotificacionEvent.class));
    }

    @Test
    void cerrarPrestamosVencidos_notifica_proximos_a_vencer() {
        // Given
        Prestamo proximoAVencer = new Prestamo();
        proximoAVencer.setId(2L);
        proximoAVencer.setUsuarioId("usuario18");
        proximoAVencer.setLibroId(20L);
        proximoAVencer.setEstado(EstadoPrestamo.ACTIVO);
        proximoAVencer.setFechaVencimiento(LocalDateTime.now().plusDays(1));

        // Sin préstamos vencidos
        when(prestamoRepository.findByEstadoAndFechaVencimientoBefore(
                eq(EstadoPrestamo.ACTIVO), any(LocalDateTime.class)))
                .thenReturn(new ArrayList<>())   // primera llamada: vencidos
                .thenReturn(Arrays.asList(proximoAVencer)); // segunda llamada: próximos a vencer

        // When
        prestamoService.cerrarPrestamosVencidos();

        // Then
        // No se cerró ningún préstamo (no hay vencidos)
        verify(licenseClient, never()).devolver(anyLong());
        // Pero sí se notificó al próximo a vencer
        verify(notificacionPublisher).publicarEvento(any(NotificacionEvent.class));
    }

    @Test
    void cerrarPrestamosVencidos_maneja_error_en_devolver() {
        // Given
        Prestamo vencido = new Prestamo();
        vencido.setId(3L);
        vencido.setUsuarioId("usuario19");
        vencido.setLibroId(30L);
        vencido.setEstado(EstadoPrestamo.ACTIVO);
        vencido.setFechaVencimiento(LocalDateTime.now().minusDays(1));

        when(prestamoRepository.findByEstadoAndFechaVencimientoBefore(
                eq(EstadoPrestamo.ACTIVO), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(vencido));
        // devolver lanza excepción
        when(licenseClient.devolver(30L))
                .thenThrow(new RuntimeException("License service error"));

        // When — no debe lanzar excepción, solo loguear error
        prestamoService.cerrarPrestamosVencidos();

        // Then
        verify(licenseClient).devolver(30L);
        // El préstamo NO debe cambiar a VENCIDO porque devolver falló
        assertEquals(EstadoPrestamo.ACTIVO, vencido.getEstado());
        verify(prestamoRepository, never()).save(any(Prestamo.class));
    }

    @Test
    void cerrarPrestamosVencidos_maneja_error_en_save() {
        // Given — devolver funciona pero save() falla
        Prestamo vencido = new Prestamo();
        vencido.setId(5L);
        vencido.setUsuarioId("usuario21");
        vencido.setLibroId(50L);
        vencido.setEstado(EstadoPrestamo.ACTIVO);
        vencido.setFechaVencimiento(LocalDateTime.now().minusDays(1));

        when(prestamoRepository.findByEstadoAndFechaVencimientoBefore(
                eq(EstadoPrestamo.ACTIVO), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(vencido));
        when(licenseClient.devolver(50L)).thenReturn(licenciaDisponible());
        when(prestamoRepository.save(any(Prestamo.class)))
                .thenThrow(new RuntimeException("Error de base de datos al guardar"));

        // When — no debe lanzar excepción, solo loguear el error
        prestamoService.cerrarPrestamosVencidos();

        // Then
        verify(licenseClient).devolver(50L);
        verify(prestamoRepository).save(any(Prestamo.class));
        // El setEstado(VENCIDO) ocurre ANTES de save() en el código,
        // por lo que el objeto en memoria sí cambió aunque save() falle
        assertEquals(EstadoPrestamo.VENCIDO, vencido.getEstado());
        // No se llegó a notificar porque save() falló antes de llegar a ese bloque
        verify(notificacionPublisher, never()).publicarEvento(any(NotificacionEvent.class));
    }

    @Test
    void cerrarPrestamosVencidos_notificacion_falla_no_rompe_flujo() {
        // Given
        Prestamo vencido = new Prestamo();
        vencido.setId(4L);
        vencido.setUsuarioId("usuario20");
        vencido.setLibroId(40L);
        vencido.setEstado(EstadoPrestamo.ACTIVO);
        vencido.setFechaVencimiento(LocalDateTime.now().minusDays(1));

        when(prestamoRepository.findByEstadoAndFechaVencimientoBefore(
                eq(EstadoPrestamo.ACTIVO), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(vencido));
        when(licenseClient.devolver(40L)).thenReturn(licenciaDisponible());
        when(prestamoRepository.save(any(Prestamo.class)))
                .thenAnswer(i -> i.getArgument(0));
        // Notificación falla
        doThrow(new RuntimeException("RabbitMQ no disponible"))
                .when(notificacionPublisher).publicarEvento(any(NotificacionEvent.class));

        // When — no debe lanzar excepción
        prestamoService.cerrarPrestamosVencidos();

        // Then
        verify(licenseClient).devolver(40L);
        verify(prestamoRepository).save(any(Prestamo.class));
        assertEquals(EstadoPrestamo.VENCIDO, vencido.getEstado());
        // La notificación se intentó pero falló — el flujo continúa
        verify(notificacionPublisher, atLeastOnce()).publicarEvento(any(NotificacionEvent.class));
    }

    @Test
    void cerrarPrestamosVencidos_sin_vencidos_no_hace_nada() {
        // Given
        when(prestamoRepository.findByEstadoAndFechaVencimientoBefore(
                eq(EstadoPrestamo.ACTIVO), any(LocalDateTime.class)))
                .thenReturn(new ArrayList<>());

        // When
        prestamoService.cerrarPrestamosVencidos();

        // Then
        verify(licenseClient, never()).devolver(anyLong());
        verify(prestamoRepository, never()).save(any(Prestamo.class));
        verify(notificacionPublisher, never()).publicarEvento(any(NotificacionEvent.class));
    }

    // ─── tests cerrarPrestamosVencidos — límites y próximos ─────────────────

    @Test
    void cerrarPrestamosVencidos_conVencidoExactamenteAhora_loProcesa() {
        // Given — préstamo cuya fechaVencimiento es exactamente LocalDateTime.now()
        // El query usa "before", no "beforeOrEqual", pero como now() se obtiene
        // antes de la consulta, un préstamo con vencimiento == now() es capturado
        Prestamo vencidoAhora = new Prestamo();
        vencidoAhora.setId(30L);
        vencidoAhora.setUsuarioId("usuario_boundary");
        vencidoAhora.setLibroId(300L);
        vencidoAhora.setEstado(EstadoPrestamo.ACTIVO);
        vencidoAhora.setFechaVencimiento(LocalDateTime.now()); // exactamente ahora

        when(prestamoRepository.findByEstadoAndFechaVencimientoBefore(
                eq(EstadoPrestamo.ACTIVO), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(vencidoAhora));
        when(licenseClient.devolver(300L)).thenReturn(licenciaDisponible());
        when(prestamoRepository.save(any(Prestamo.class)))
                .thenAnswer(i -> i.getArgument(0));

        // When
        prestamoService.cerrarPrestamosVencidos();

        // Then — se procesa como vencido (now().isAfter(vencimiento) es falso,
        // pero la query usa before y now() se captura antes del query)
        verify(licenseClient).devolver(300L);
        verify(prestamoRepository).save(any(Prestamo.class));
        assertEquals(EstadoPrestamo.VENCIDO, vencidoAhora.getEstado());
    }

    @Test
    void cerrarPrestamosVencidos_notificacionProximoFalla_noRompeFlujo() {
        // Given — un préstamo próximo a vencer cuya notificación falla
        Prestamo proximo = new Prestamo();
        proximo.setId(31L);
        proximo.setUsuarioId("usuario_prox_fail");
        proximo.setLibroId(310L);
        proximo.setEstado(EstadoPrestamo.ACTIVO);
        proximo.setFechaVencimiento(LocalDateTime.now().plusDays(1));

        when(prestamoRepository.findByEstadoAndFechaVencimientoBefore(
                eq(EstadoPrestamo.ACTIVO), any(LocalDateTime.class)))
                .thenReturn(new ArrayList<>())           // vencidos: vacío
                .thenReturn(Arrays.asList(proximo));     // próximos: 1
        // Notificación falla
        doThrow(new RuntimeException("RabbitMQ no disponible"))
                .when(notificacionPublisher).publicarEvento(any(NotificacionEvent.class));

        // When — no debe lanzar excepción
        prestamoService.cerrarPrestamosVencidos();

        // Then
        verify(licenseClient, never()).devolver(anyLong());
        verify(notificacionPublisher).publicarEvento(any(NotificacionEvent.class)); // se intentó notificar
    }

    @Test
    void cerrarPrestamosVencidos_conMultiplesProximosYUnaNotificacionFalla_procesaRestante() {
        // Given — dos préstamos próximos a vencer, la notificación del primero falla
        Prestamo proximo1 = new Prestamo();
        proximo1.setId(32L);
        proximo1.setUsuarioId("usuario_prox1");
        proximo1.setLibroId(320L);
        proximo1.setEstado(EstadoPrestamo.ACTIVO);
        proximo1.setFechaVencimiento(LocalDateTime.now().plusDays(1));

        Prestamo proximo2 = new Prestamo();
        proximo2.setId(33L);
        proximo2.setUsuarioId("usuario_prox2");
        proximo2.setLibroId(330L);
        proximo2.setEstado(EstadoPrestamo.ACTIVO);
        proximo2.setFechaVencimiento(LocalDateTime.now().plusDays(1));

        when(prestamoRepository.findByEstadoAndFechaVencimientoBefore(
                eq(EstadoPrestamo.ACTIVO), any(LocalDateTime.class)))
                .thenReturn(new ArrayList<>())               // vencidos
                .thenReturn(Arrays.asList(proximo1, proximo2)); // próximos

        // La notificación falla para el primero pero funciona para el segundo
        doThrow(new RuntimeException("RabbitMQ no disponible"))
                .doNothing()
                .when(notificacionPublisher).publicarEvento(any(NotificacionEvent.class));

        // When — no debe lanzar excepción
        prestamoService.cerrarPrestamosVencidos();

        // Then — ambas notificaciones se intentaron
        verify(notificacionPublisher, times(2)).publicarEvento(any(NotificacionEvent.class));
    }

    // ─── tests crearPrestamo — verificación del objeto guardado ─────────────

    @Test
    void crearPrestamo_verificaObjetoGuardado_tieneCamposCorrectos() {
        // Given — flujo exitoso completo
        String usuarioId = "usuario_captor";
        Long libroId = 777L;
        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(suscripcionBasico());
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(prestamoRepository.findByLibroIdAndEstado(libroId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(licenseClient.obtenerLicencia(libroId)).thenReturn(licenciaDisponible());
        when(licenseClient.prestar(libroId)).thenReturn(licenciaDisponible());
        when(prestamoRepository.save(any(Prestamo.class)))
                .thenAnswer(i -> i.getArgument(0));

        // When
        prestamoService.crearPrestamo(request(libroId), usuarioId);

        // Then — capturar el objeto pasado a save() y verificar sus campos
        ArgumentCaptor<Prestamo> captor = ArgumentCaptor.forClass(Prestamo.class);
        verify(prestamoRepository).save(captor.capture());
        Prestamo prestamoGuardado = captor.getValue();

        assertNotNull(prestamoGuardado);
        assertEquals(usuarioId, prestamoGuardado.getUsuarioId());
        assertEquals(libroId, prestamoGuardado.getLibroId());
        assertEquals(EstadoPrestamo.ACTIVO, prestamoGuardado.getEstado());
        assertNotNull(prestamoGuardado.getFechaInicio());
        assertNotNull(prestamoGuardado.getFechaVencimiento());
        // Plan BASICO: exactamente 7 días después
        assertEquals(
                prestamoGuardado.getFechaInicio().plusDays(7),
                prestamoGuardado.getFechaVencimiento());
    }

    @Test
    void crearPrestamo_verificaObjetoGuardado_conPlanPremium_camposCorrectos() {
        // Given — flujo exitoso con plan PREMIUM
        String usuarioId = "usuario_captor_premium";
        Long libroId = 778L;
        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(suscripcionPremium());
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(prestamoRepository.findByLibroIdAndEstado(libroId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(licenseClient.obtenerLicencia(libroId)).thenReturn(licenciaDisponible());
        when(licenseClient.prestar(libroId)).thenReturn(licenciaDisponible());
        when(prestamoRepository.save(any(Prestamo.class)))
                .thenAnswer(i -> i.getArgument(0));

        // When
        prestamoService.crearPrestamo(request(libroId), usuarioId);

        // Then — verificar campos con PREMIUM
        ArgumentCaptor<Prestamo> captor = ArgumentCaptor.forClass(Prestamo.class);
        verify(prestamoRepository).save(captor.capture());
        Prestamo prestamoGuardado = captor.getValue();

        assertNotNull(prestamoGuardado);
        assertEquals(usuarioId, prestamoGuardado.getUsuarioId());
        assertEquals(libroId, prestamoGuardado.getLibroId());
        assertEquals(EstadoPrestamo.ACTIVO, prestamoGuardado.getEstado());
        // Plan PREMIUM: exactamente 14 días después
        assertEquals(
                prestamoGuardado.getFechaInicio().plusDays(14),
                prestamoGuardado.getFechaVencimiento());
    }

    // ─── tests adicionales — edge cases ───────────────────────────────────

    @Test
    void crearPrestamo_conSuscripcionInactiva_funcionaIgual() {
        // Given — subscription con activa=false pero datos de plan válidos
        // El código no verifica el campo activa, solo usa maxPrestamos y diasPrestamo
        String usuarioId = "usuario22";
        SuscripcionDTO suscripcionInactiva = new SuscripcionDTO();
        suscripcionInactiva.setPlan("PREMIUM");
        suscripcionInactiva.setMaxPrestamos(5);
        suscripcionInactiva.setDiasPrestamo(14);
        suscripcionInactiva.setActiva(false); // ← inactiva

        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(suscripcionInactiva);
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(prestamoRepository.findByLibroIdAndEstado(22L, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(licenseClient.obtenerLicencia(22L)).thenReturn(licenciaDisponible());
        when(licenseClient.prestar(22L)).thenReturn(licenciaDisponible());
        when(prestamoRepository.save(any(Prestamo.class))).thenAnswer(i -> i.getArgument(0));

        // When — debe funcionar pese a activa=false
        PrestamoResponseDTO resultado = prestamoService.crearPrestamo(request(22L), usuarioId);

        // Then
        assertNotNull(resultado);
        assertEquals(22L, resultado.getLibroId());
        // Con PREMIUM: 14 días
        assertTrue(resultado.getFechaVencimiento()
                .isAfter(resultado.getFechaInicio().plusDays(13)));
        verify(prestamoRepository).save(any(Prestamo.class));
    }

    @Test
    void cerrarPrestamosVencidos_conMultiplesVencidos_procesaRestanteCuandoUnoFalla() {
        // Given — dos préstamos vencidos, el primero falla en devolver()
        Prestamo vencido1 = new Prestamo();
        vencido1.setId(10L);
        vencido1.setUsuarioId("user_fail");
        vencido1.setLibroId(100L);
        vencido1.setEstado(EstadoPrestamo.ACTIVO);
        vencido1.setFechaVencimiento(LocalDateTime.now().minusDays(1));

        Prestamo vencido2 = new Prestamo();
        vencido2.setId(11L);
        vencido2.setUsuarioId("user_ok");
        vencido2.setLibroId(101L);
        vencido2.setEstado(EstadoPrestamo.ACTIVO);
        vencido2.setFechaVencimiento(LocalDateTime.now().minusDays(2));

        when(prestamoRepository.findByEstadoAndFechaVencimientoBefore(
                eq(EstadoPrestamo.ACTIVO), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(vencido1, vencido2));
        when(licenseClient.devolver(100L)).thenThrow(new RuntimeException("License error"));
        when(licenseClient.devolver(101L)).thenReturn(licenciaDisponible());
        when(prestamoRepository.save(any(Prestamo.class))).thenAnswer(i -> i.getArgument(0));

        // When
        prestamoService.cerrarPrestamosVencidos();

        // Then
        verify(licenseClient).devolver(100L); // se intentó
        verify(licenseClient).devolver(101L); // también se procesó
        assertEquals(EstadoPrestamo.ACTIVO, vencido1.getEstado());  // falló → sin cambios
        assertEquals(EstadoPrestamo.VENCIDO, vencido2.getEstado()); // éxito → cambió
        verify(prestamoRepository, times(1)).save(any(Prestamo.class)); // solo el segundo
    }

    @Test
    void crearPrestamo_cuandoSubscriptionRetornaNull_aplicaBasicoPorDefecto() {
        // Given — subscription retorna null (Feign 200 OK con body null)
        // El NPE en suscripcion.getPlan() es capturado por el try/catch y aplica BASICO
        String usuarioId = "usuario23";
        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(null);

        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(prestamoRepository.findByLibroIdAndEstado(23L, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(licenseClient.obtenerLicencia(23L)).thenReturn(licenciaDisponible());
        when(licenseClient.prestar(23L)).thenReturn(licenciaDisponible());
        when(prestamoRepository.save(any(Prestamo.class))).thenAnswer(i -> i.getArgument(0));

        // When
        PrestamoResponseDTO resultado = prestamoService.crearPrestamo(request(23L), usuarioId);

        // Then — aplica BASICO por defecto (7 días)
        assertNotNull(resultado);
        assertEquals(23L, resultado.getLibroId());
        assertTrue(resultado.getFechaVencimiento()
                .isAfter(resultado.getFechaInicio().plusDays(6)));
        verify(prestamoRepository).save(any(Prestamo.class));
    }

    // ─── test optimistic lock — 409 Conflict ─────────────────────────────────

    @Test
    void crearPrestamo_falla_cuando_licenseClient_prestar_lanza_409_conflict() {
        // Given — simula el optimistic lock: License Service responde 409
        // cuando dos usuarios piden la última copia simultáneamente
        String usuarioId = "usuario_optimistic_lock";
        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(suscripcionBasico());
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(prestamoRepository.findByLibroIdAndEstado(42L, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(licenseClient.obtenerLicencia(42L)).thenReturn(licenciaDisponible());
        when(licenseClient.prestar(42L))
                .thenThrow(mock(FeignException.Conflict.class));

        // When & Then
        UltimaCopiaNoDisponibleException ex = assertThrows(UltimaCopiaNoDisponibleException.class,
                () -> prestamoService.crearPrestamo(request(42L), usuarioId));

        assertTrue(ex.getMessage().contains("última copia")
                || ex.getMessage().contains("otro usuario")
                || ex.getMessage().contains("Intenta de nuevo"));
        // El wrapper reintenta 3 veces — verify que prestar() se llamó 3 veces
        verify(licenseClient, times(3)).prestar(42L);
        // No debe llegar a save — la copia no se descontó
        verify(prestamoRepository, never()).save(any(Prestamo.class));
    }

    @Test
    void crearPrestamo_funciona_en_3er_reintento_cuando_license_devuelve_409_dos_veces() {
        // Given — License Service responde 409 dos veces y éxito en el 3er intento
        String usuarioId = "usuario_409_3er_intento";
        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(suscripcionBasico());
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(prestamoRepository.findByLibroIdAndEstado(88L, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(licenseClient.obtenerLicencia(88L)).thenReturn(licenciaDisponible());
        when(licenseClient.prestar(88L))
                .thenThrow(mock(FeignException.Conflict.class))
                .thenThrow(mock(FeignException.Conflict.class))
                .thenReturn(licenciaDisponible());
        when(prestamoRepository.save(any(Prestamo.class)))
                .thenAnswer(i -> i.getArgument(0));

        // When
        PrestamoResponseDTO resultado = prestamoService.crearPrestamo(request(88L), usuarioId);

        // Then — el 3er reintento fue exitoso
        assertNotNull(resultado);
        assertEquals(88L, resultado.getLibroId());
        assertEquals(EstadoPrestamo.ACTIVO, resultado.getEstado());
        verify(licenseClient, times(3)).prestar(88L);
        verify(prestamoRepository).save(any(Prestamo.class));
    }

    // ─── test optimistic lock local — @Version en Prestamo ───────────────────

    @Test
    void crearPrestamo_falla_cuando_save_lanza_optimistic_lock() {
        // Given — simula el optimistic lock local: @Version en Prestamo
        // detecta que otro request creó un préstamo concurrentemente
        String usuarioId = "usuario_ol_local";
        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(suscripcionBasico());
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(prestamoRepository.findByLibroIdAndEstado(99L, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(licenseClient.obtenerLicencia(99L)).thenReturn(licenciaDisponible());
        when(licenseClient.prestar(99L)).thenReturn(licenciaDisponible());
        when(prestamoRepository.save(any(Prestamo.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException("prestamo", null));

        // When & Then
        UltimaCopiaNoDisponibleException ex = assertThrows(UltimaCopiaNoDisponibleException.class,
                () -> prestamoService.crearPrestamo(request(99L), usuarioId));

        assertTrue(ex.getMessage().contains("última copia")
                || ex.getMessage().contains("otro usuario")
                || ex.getMessage().contains("Intenta de nuevo"));
        // La copia se descontó en license-service pero falló el save local
        verify(licenseClient).prestar(99L);
        // Debe compensar: devolver la copia
        verify(licenseClient).devolver(99L);
    }

    @Test
    void crearPrestamo_compensa_cuando_save_lanza_OL_y_devolver_tambien_falla() {
        // Given — @Version en Prestamo lanza OL en save(), y la compensación
        // (devolver copia en license-service) también falla
        String usuarioId = "usuario_ol_comp_fail";
        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(suscripcionBasico());
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(prestamoRepository.findByLibroIdAndEstado(55L, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(licenseClient.obtenerLicencia(55L)).thenReturn(licenciaDisponible());
        when(licenseClient.prestar(55L)).thenReturn(licenciaDisponible());
        when(prestamoRepository.save(any(Prestamo.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException("prestamo", null));
        // La compensación también falla
        when(licenseClient.devolver(55L))
                .thenThrow(new RuntimeException("License service no disponible para compensar OL"));

        // When & Then — debe lanzar UltimaCopiaNoDisponibleException a pesar del fallo en compensación
        UltimaCopiaNoDisponibleException ex = assertThrows(UltimaCopiaNoDisponibleException.class,
                () -> prestamoService.crearPrestamo(request(55L), usuarioId));

        assertTrue(ex.getMessage().contains("última copia")
                || ex.getMessage().contains("otro usuario")
                || ex.getMessage().contains("Intenta de nuevo"));
        // La copia se descontó y se intentó compensar
        verify(licenseClient).prestar(55L);
        verify(licenseClient).devolver(55L);
        // No debe llegar a guardar exitosamente
        verify(prestamoRepository).save(any(Prestamo.class));
    }

    // ─── test scheduler — ObjectOptimisticLockingFailureException ────────────

    @Test
    void cerrarPrestamosVencidos_maneja_OL_y_continua_con_otros_prestamos() {
        // Given — dos préstamos vencidos, el primero lanza OL en save,
        // el segundo se procesa correctamente
        Prestamo vencido1 = new Prestamo();
        vencido1.setId(60L);
        vencido1.setUsuarioId("user_ol_fail");
        vencido1.setLibroId(600L);
        vencido1.setEstado(EstadoPrestamo.ACTIVO);
        vencido1.setFechaVencimiento(LocalDateTime.now().minusDays(1));

        Prestamo vencido2 = new Prestamo();
        vencido2.setId(61L);
        vencido2.setUsuarioId("user_ol_ok");
        vencido2.setLibroId(601L);
        vencido2.setEstado(EstadoPrestamo.ACTIVO);
        vencido2.setFechaVencimiento(LocalDateTime.now().minusDays(2));

        when(prestamoRepository.findByEstadoAndFechaVencimientoBefore(
                eq(EstadoPrestamo.ACTIVO), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(vencido1, vencido2));
        when(licenseClient.devolver(600L)).thenReturn(licenciaDisponible());
        when(licenseClient.devolver(601L)).thenReturn(licenciaDisponible());
        // El save del primero lanza OL, el segundo funciona
        when(prestamoRepository.save(any(Prestamo.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException("prestamo", null))
                .thenAnswer(i -> i.getArgument(0));

        // When — no debe lanzar excepción, continúa con el segundo aunque
        // el primero falle por OL
        prestamoService.cerrarPrestamosVencidos();

        // Then
        verify(licenseClient).devolver(600L);  // primero: devolver se llamó
        verify(licenseClient).devolver(601L);  // segundo: devolver se llamó
        verify(prestamoRepository, times(2)).save(any(Prestamo.class)); // ambos intentaron save
        // NOTA: setEstado(VENCIDO) se ejecuta ANTES de save() en el código,
        // por lo que vencido1.getEstado() = VENCIDO aunque save() haya fallado.
        // Lo que importa es que el flujo no se interrumpió y procesó al segundo.
        // El segundo se procesó correctamente
        assertEquals(EstadoPrestamo.VENCIDO, vencido2.getEstado());
    }

    // ─── test de concurrencia — CountDownLatch ────────────────────────────

    @Test
    void crearPrestamo_concurrencia_ultimaCopia_conCountDownLatch() throws InterruptedException {
        // Given — 3 hilos compiten por la última copia del libro 999
        // Usando CountDownLatch para que todos arranquen simultáneamente
        String usuarioId = "usuario_concurrente";
        Long libroId = 999L;
        int numHilos = 3;

        // Mock: 1 copia disponible para todos
        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(suscripcionBasico());
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(prestamoRepository.findByLibroIdAndEstado(libroId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(licenseClient.obtenerLicencia(libroId)).thenReturn(licenciaDisponible());

        // Simula la carrera: el primer hilo que llama a prestar() descuenta la última copia,
        // los siguientes reciben 409 Conflict y reintentan hasta agotar los 3 intentos.
        AtomicBoolean copiaDescontada = new AtomicBoolean(false);
        when(licenseClient.prestar(libroId)).thenAnswer(invocation -> {
            if (copiaDescontada.compareAndSet(false, true)) {
                return licenciaDisponible(); // el primero en llegar descuenta
            }
            throw mock(FeignException.Conflict.class); // los demás chocan
        });

        // El primero que pasa prestar() puede guardar el préstamo
        when(prestamoRepository.save(any(Prestamo.class)))
                .thenAnswer(i -> i.getArgument(0));

        // CountDownLatch: todos los hilos esperan en el mismo punto de partida
        CountDownLatch latch = new CountDownLatch(1);
        // Contadores para resultados
        AtomicInteger exitosos = new AtomicInteger(0);
        AtomicInteger fallidos = new AtomicInteger(0);
        // Excepción capturada para verificar mensaje
        AtomicReference<Throwable> excepcionCapturada = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(numHilos);

        for (int i = 0; i < numHilos; i++) {
            executor.submit(() -> {
                try {
                    latch.await(); // todos esperan aquí
                    prestamoService.crearPrestamo(request(libroId), usuarioId);
                    exitosos.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    fallidos.incrementAndGet();
                } catch (RuntimeException e) {
                    excepcionCapturada.set(e);
                    fallidos.incrementAndGet();
                }
            });
        }

        // Disparo de salida — todos arrancan a la vez
        latch.countDown();

        // Esperar a que todos terminen — awaitTermination reemplaza Thread.sleep
        // para evitar fragilidad en entornos CI lentos
        executor.shutdown();
        if (!executor.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }

        // Then
        // Solo 1 hilo logró descontar la copia y crear el préstamo
        assertEquals(1, exitosos.get(),
                "Solo un hilo debería obtener la última copia exitosamente");
        assertEquals(numHilos - 1, fallidos.get(),
                "Los demás hilos deben fallar tras agotar reintentos");
        assertNotNull(excepcionCapturada.get(),
                "Debe capturarse al menos una excepción de los hilos fallidos");
        assertTrue(excepcionCapturada.get().getMessage().contains("última copia")
                        || excepcionCapturada.get().getMessage().contains("otro usuario"),
                "El mensaje de error debe indicar que la copia fue tomada por otro usuario");
        // prestar() fue llamado 1 vez (éxito) + 3 * (numHilos - 1) reintentos
        verify(licenseClient, times(1 + 3 * (numHilos - 1))).prestar(libroId);
        // save() solo se llamó una vez (el exitoso)
        verify(prestamoRepository, times(1)).save(any(Prestamo.class));
    }

    @Test
    void crearPrestamo_concurrencia_5_hilos_conCountDownLatch() throws InterruptedException {
        // Given — 5 hilos compiten por la última copia del libro 888
        // Extiende el test original para verificar comportamiento con más hilos
        String usuarioId = "usuario_5hilos";
        Long libroId = 888L;
        int numHilos = 5;

        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(suscripcionBasico());
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(prestamoRepository.findByLibroIdAndEstado(libroId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(licenseClient.obtenerLicencia(libroId)).thenReturn(licenciaDisponible());

        AtomicBoolean copiaDescontada = new AtomicBoolean(false);
        when(licenseClient.prestar(libroId)).thenAnswer(invocation -> {
            if (copiaDescontada.compareAndSet(false, true)) {
                return licenciaDisponible();
            }
            throw mock(FeignException.Conflict.class);
        });

        when(prestamoRepository.save(any(Prestamo.class)))
                .thenAnswer(i -> i.getArgument(0));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger exitosos = new AtomicInteger(0);
        AtomicInteger fallidos = new AtomicInteger(0);
        AtomicReference<Throwable> excepcionCapturada = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(numHilos);

        for (int i = 0; i < numHilos; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    prestamoService.crearPrestamo(request(libroId), usuarioId);
                    exitosos.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    fallidos.incrementAndGet();
                } catch (RuntimeException e) {
                    excepcionCapturada.set(e);
                    fallidos.incrementAndGet();
                }
            });
        }

        latch.countDown();

        executor.shutdown();
        if (!executor.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }

        // Then
        assertEquals(1, exitosos.get(),
                "Solo un hilo de 5 debería obtener la última copia");
        assertEquals(numHilos - 1, fallidos.get(),
                "Los 4 hilos restantes deben fallar tras agotar reintentos");
        assertNotNull(excepcionCapturada.get());
        assertTrue(excepcionCapturada.get().getMessage().contains("última copia")
                        || excepcionCapturada.get().getMessage().contains("otro usuario"));
        // prestar(): 1 éxito + 3 reintentos * 4 hilos fallidos = 13
        verify(licenseClient, times(1 + 3 * (numHilos - 1))).prestar(libroId);
        verify(prestamoRepository, times(1)).save(any(Prestamo.class));
    }

    @Test
    void crearPrestamo_concurrencia_10_hilos_conCountDownLatch() throws InterruptedException {
        // Given — 10 hilos compiten por la última copia del libro 777
        // Verifica que el patrón de reintentos escala correctamente
        String usuarioId = "usuario_10hilos";
        Long libroId = 777L;
        int numHilos = 10;

        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(suscripcionBasico());
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(prestamoRepository.findByLibroIdAndEstado(libroId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(licenseClient.obtenerLicencia(libroId)).thenReturn(licenciaDisponible());

        AtomicBoolean copiaDescontada = new AtomicBoolean(false);
        when(licenseClient.prestar(libroId)).thenAnswer(invocation -> {
            if (copiaDescontada.compareAndSet(false, true)) {
                return licenciaDisponible();
            }
            throw mock(FeignException.Conflict.class);
        });

        when(prestamoRepository.save(any(Prestamo.class)))
                .thenAnswer(i -> i.getArgument(0));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger exitosos = new AtomicInteger(0);
        AtomicInteger fallidos = new AtomicInteger(0);
        AtomicReference<Throwable> excepcionCapturada = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(numHilos);

        for (int i = 0; i < numHilos; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    prestamoService.crearPrestamo(request(libroId), usuarioId);
                    exitosos.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    fallidos.incrementAndGet();
                } catch (RuntimeException e) {
                    excepcionCapturada.set(e);
                    fallidos.incrementAndGet();
                }
            });
        }

        latch.countDown();

        executor.shutdown();
        if (!executor.awaitTermination(20, java.util.concurrent.TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }

        // Then
        assertEquals(1, exitosos.get(),
                "Solo un hilo de 10 debería obtener la última copia");
        assertEquals(numHilos - 1, fallidos.get(),
                "Los 9 hilos restantes deben fallar tras agotar reintentos");
        assertNotNull(excepcionCapturada.get());
        assertTrue(excepcionCapturada.get().getMessage().contains("última copia")
                        || excepcionCapturada.get().getMessage().contains("otro usuario"));
        // prestar(): 1 éxito + 3 reintentos * 9 hilos fallidos = 28
        verify(licenseClient, times(1 + 3 * (numHilos - 1))).prestar(libroId);
        verify(prestamoRepository, times(1)).save(any(Prestamo.class));
    }

    @Test
    void crearPrestamo_concurrencia_conLatenciaVariable() throws InterruptedException {
        // Given — 3 hilos con latencia variable en prestar() simulando red
        // Verifica que el CountDownLatch + retry funciona incluso con tiempos de red irregulares
        String usuarioId = "usuario_latencia";
        Long libroId = 666L;
        int numHilos = 3;

        when(subscriptionClient.obtenerSuscripcion(usuarioId)).thenReturn(suscripcionBasico());
        when(prestamoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(prestamoRepository.findByLibroIdAndEstado(libroId, EstadoPrestamo.ACTIVO))
                .thenReturn(new ArrayList<>());
        when(licenseClient.obtenerLicencia(libroId)).thenReturn(licenciaDisponible());

        // Simular latencia de red variable (80-200ms) en prestar()
        AtomicBoolean copiaDescontada = new AtomicBoolean(false);
        when(licenseClient.prestar(libroId)).thenAnswer(invocation -> {
            Thread.sleep(80 + (long)(Math.random() * 120)); // 80-200ms de latencia
            if (copiaDescontada.compareAndSet(false, true)) {
                return licenciaDisponible();
            }
            throw mock(FeignException.Conflict.class);
        });

        when(prestamoRepository.save(any(Prestamo.class)))
                .thenAnswer(i -> i.getArgument(0));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger exitosos = new AtomicInteger(0);
        AtomicInteger fallidos = new AtomicInteger(0);
        AtomicReference<Throwable> excepcionCapturada = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(numHilos);

        for (int i = 0; i < numHilos; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    prestamoService.crearPrestamo(request(libroId), usuarioId);
                    exitosos.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    fallidos.incrementAndGet();
                } catch (RuntimeException e) {
                    excepcionCapturada.set(e);
                    fallidos.incrementAndGet();
                }
            });
        }

        latch.countDown();

        executor.shutdown();
        // Mayor timeout por latencia simulada
        if (!executor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }

        // Then
        assertEquals(1, exitosos.get(),
                "Solo un hilo debería obtener la última copia con latencia variable");
        assertEquals(numHilos - 1, fallidos.get(),
                "Los demás hilos deben fallar incluso con latencia simulada");
        assertNotNull(excepcionCapturada.get());
        assertTrue(excepcionCapturada.get().getMessage().contains("última copia")
                        || excepcionCapturada.get().getMessage().contains("otro usuario"));
        verify(licenseClient, times(1 + 3 * (numHilos - 1))).prestar(libroId);
        verify(prestamoRepository, times(1)).save(any(Prestamo.class));
    }
}