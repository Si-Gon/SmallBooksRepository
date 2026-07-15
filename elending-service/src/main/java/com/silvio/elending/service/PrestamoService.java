package com.silvio.elending.service;

import com.silvio.elending.client.LicenseClient;
import com.silvio.elending.client.SubscriptionClient;
import com.silvio.elending.dto.LicenciaDTO;
import com.silvio.elending.dto.PrestamoRequestDTO;
import com.silvio.elending.dto.PrestamoResponseDTO;
import com.silvio.elending.dto.SuscripcionDTO;
import com.silvio.elending.messaging.NotificacionEvent;
import com.silvio.elending.messaging.NotificacionPublisher;
import com.silvio.elending.model.Prestamo;
import com.silvio.elending.model.Prestamo.EstadoPrestamo;
import com.silvio.elending.repository.PrestamoRepository;
import com.silvio.elending.exception.CopiaNoDisponibleException;
import com.silvio.elending.exception.ErrorCreacionPrestamoException;
import com.silvio.elending.exception.LimitePrestamosExcedidoException;
import com.silvio.elending.exception.PrestamoDuplicadoException;
import com.silvio.elending.exception.UltimaCopiaNoDisponibleException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.observation.annotation.Observed;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrestamoService {

    private final PrestamoRepository prestamoRepository;
    private final LicenseClient licenseClient;
    private final SubscriptionClient subscriptionClient;
    private final NotificacionPublisher notificacionPublisher;

    // Auto-inyeccion para sortear self-invocation de @Transactional.
    // Sin esto, llamar doCrearPrestamo() desde crearPrestamo() bypassearia
    // el proxy AOP y @Transactional no se aplicaria.
    @Autowired
    @Lazy
    private PrestamoService self;

    // ─── crearPrestamo con optimistic locking ────────────────────────────────
    // Wrapper no transaccional: reintenta hasta 3 veces si el License Service
    // responde 409 Conflict (otro usuario tomó la última copia justo antes).
    // El @Version en License dispara ObjectOptimisticLockingFailureException
    // que el License Service convierte en 409 tras agotar reintentos.
    // El @Version en Prestamo protege el save() local contra escrituras concurrentes.

    @Observed(name = "elending.crearPrestamo")
    public PrestamoResponseDTO crearPrestamo(PrestamoRequestDTO request, String usuarioId) {
        int maxReintentos = 3;
        int intento = 0;
        while (true) {
            try {
                return self.doCrearPrestamo(request, usuarioId);
            } catch (FeignException.Conflict e) {
                // 409 del License Service — otro usuario tomó la última copia justo antes
                if (++intento >= maxReintentos) {
                    log.warn("No se pudo crear préstamo tras {} intentos — libro: {}, usuario: {}",
                            maxReintentos, request.getLibroId(), usuarioId);
                    throw new UltimaCopiaNoDisponibleException();
                }
                log.warn("Conflicto de concurrencia al descontar copia — libro: {}, reintento {}/{}",
                        request.getLibroId(), intento, maxReintentos);
            }
        }
    }

    @Observed(name = "elending.doCrearPrestamo")
    @Transactional
    protected PrestamoResponseDTO doCrearPrestamo(PrestamoRequestDTO request, String usuarioId) {
        log.info("Iniciando creación de préstamo — usuario: {}, libro: {}", 
                usuarioId, request.getLibroId());

        // Paso 1: Consultar plan del usuario
        SuscripcionDTO suscripcion;
        try {
            suscripcion = subscriptionClient.obtenerSuscripcion(usuarioId);
            log.info("Plan activo para usuario {}: {} — max: {} préstamos, {} días",
                    usuarioId, suscripcion.getPlan(),
                    suscripcion.getMaxPrestamos(), suscripcion.getDiasPrestamo());
        } catch (Exception e) {
            log.warn("No se encontró suscripción activa para usuario {}. Aplicando plan BASICO por defecto.", 
                    usuarioId);
            suscripcion = new SuscripcionDTO();
            suscripcion.setMaxPrestamos(2);
            suscripcion.setDiasPrestamo(7);
            suscripcion.setPlan("BASICO");
        }

        // Validar que maxPrestamos y diasPrestamo no sean nulos ni inválidos
        // Si son nulos o <= 0 (ej. respuesta incompleta del subscription-service), aplicar plan BASICO por defecto
        if (suscripcion.getMaxPrestamos() == null || suscripcion.getDiasPrestamo() == null
                || suscripcion.getMaxPrestamos() <= 0 || suscripcion.getDiasPrestamo() <= 0) {
            log.warn("Valores inválidos en suscripción del usuario {}. Aplicando plan BASICO por defecto. " +
                     "maxPrestamos: {}, diasPrestamo: {}",
                     usuarioId, suscripcion.getMaxPrestamos(), suscripcion.getDiasPrestamo());
            if (suscripcion.getMaxPrestamos() == null || suscripcion.getMaxPrestamos() <= 0) {
                suscripcion.setMaxPrestamos(2);
            }
            if (suscripcion.getDiasPrestamo() == null || suscripcion.getDiasPrestamo() <= 0) {
                suscripcion.setDiasPrestamo(7);
            }
        }

        // Paso 2: Verificar límite de préstamos
        List<Prestamo> prestamosActivos = prestamoRepository
                .findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO);

        if (prestamosActivos.size() >= suscripcion.getMaxPrestamos()) {
            log.warn("Usuario {} alcanzó límite de préstamos activos: {}/{}",
                    usuarioId, prestamosActivos.size(), suscripcion.getMaxPrestamos());
            throw new LimitePrestamosExcedidoException(
                    suscripcion.getMaxPrestamos(), suscripcion.getPlan());
        }

        // Paso 3: Verificar duplicado
        boolean yaLoTiene = prestamoRepository
                .findByLibroIdAndEstado(request.getLibroId(), EstadoPrestamo.ACTIVO)
                .stream()
                .anyMatch(p -> p.getUsuarioId().equals(usuarioId));

        if (yaLoTiene) {
            log.warn("Usuario {} ya tiene préstamo activo del libro {}", 
                    usuarioId, request.getLibroId());
            throw new PrestamoDuplicadoException();
        }

        // Paso 4: Verificar copias disponibles
        LicenciaDTO licencia = licenseClient.obtenerLicencia(request.getLibroId());
        log.info("Licencia libro {}: {}/{} copias disponibles",
                request.getLibroId(), licencia.getCopiasDisponibles(), licencia.getTotalCopias());

        if (licencia.getCopiasDisponibles() == null || licencia.getCopiasDisponibles() <= 0) {
            log.warn("No hay copias disponibles del libro {}", request.getLibroId());
            throw new CopiaNoDisponibleException(request.getLibroId());
        }

        // Paso 5: Descontar copia
        // El License Service usa @Version + reintentos (optimistic locking).
        // Si tras 3 reintentos persiste el conflicto, responde 409 Conflict,
        // que el wrapper captura y reintenta.
        licenseClient.prestar(request.getLibroId());
        log.info("Copia descontada exitosamente — libro: {}", request.getLibroId());

        // Paso 6: Crear préstamo
        LocalDateTime ahora = LocalDateTime.now();
        Prestamo prestamo = new Prestamo();
        prestamo.setUsuarioId(usuarioId);
        prestamo.setLibroId(request.getLibroId());
        prestamo.setFechaInicio(ahora);
        prestamo.setFechaVencimiento(ahora.plusDays(suscripcion.getDiasPrestamo()));
        prestamo.setEstado(EstadoPrestamo.ACTIVO);

        Prestamo guardado;
        try {
            guardado = prestamoRepository.save(prestamo);
            log.info("Préstamo creado exitosamente — id: {}, usuario: {}, libro: {}, vence: {}",
            guardado.getId(), usuarioId, request.getLibroId(), 
            guardado.getFechaVencimiento());
        } catch (ObjectOptimisticLockingFailureException e) {
            // Conflicto de @Version en Prestamo — otro request creó el mismo préstamo
            log.error("Conflicto de concurrencia al guardar préstamo — libro: {}: {}",
                    request.getLibroId(), e.getMessage());
            try {
                licenseClient.devolver(request.getLibroId());
                log.info("Compensación exitosa — copia restaurada para libro: {}", 
                        request.getLibroId());
            } catch (Exception ex) {
                log.error("COMPENSACIÓN FALLIDA — inconsistencia en copias del libro: {}. " +
                        "Requiere revisión manual.", request.getLibroId());
            }
            throw new UltimaCopiaNoDisponibleException();
        } catch (Exception e) {
            log.error("Error al guardar préstamo — intentando compensar descuento de copia — libro: {}",
            request.getLibroId());
            try {
                licenseClient.devolver(request.getLibroId());
                log.info("Compensación exitosa — copia restaurada para libro: {}", 
                        request.getLibroId());
            } catch (Exception ex) {
                log.error("COMPENSACIÓN FALLIDA — inconsistencia en copias del libro: {}. " +
                        "Requiere revisión manual.", request.getLibroId());
            }
            throw new ErrorCreacionPrestamoException();
        }

        // Paso 7: Notificar vía RabbitMQ (asíncrono — reemplaza Feign)
        try {
            notificacionPublisher.publicarEvento(
                NotificacionEvent.prestamoCreado(usuarioId, request.getLibroId(), suscripcion.getDiasPrestamo()));
            log.info("Evento PRESTAMO_CREADO publicado para usuario {}", usuarioId);
        } catch (Exception e) {
            log.warn("No se pudo publicar evento de préstamo creado para usuario {}: {}",
                    usuarioId, e.getMessage());
        }

        return mapearADto(guardado);
    }

    @Observed(name = "elending.obtenerPrestamosActivos")
    public List<PrestamoResponseDTO> obtenerPrestamosActivos(String usuarioId) {
        log.info("Consultando préstamos activos del usuario: {}", usuarioId);
        return prestamoRepository
                .findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO)
                .stream()
                .map(this::mapearADto)
                .collect(Collectors.toList());
    }

    @Observed(name = "elending.obtenerHistorial")
    public List<PrestamoResponseDTO> obtenerHistorial(String usuarioId) {
        log.info("Consultando historial completo del usuario: {}", usuarioId);
        return prestamoRepository
                .findByUsuarioId(usuarioId)
                .stream()
                .map(this::mapearADto)
                .collect(Collectors.toList());
    }

    @Observed(name = "elending.obtenerTodos")
    public List<PrestamoResponseDTO> obtenerTodos() {
        log.info("Consultando todos los préstamos para Analytics");
        return prestamoRepository.findAll()
                .stream()
                .map(this::mapearADto)
                .collect(Collectors.toList());
    }

    // ShedLock: bloqueo distribuido para multi-instancia.
    // Solo una instancia adquiere el lock "prestamos-vencidos" por vez.
    // lockAtMostFor = 30m: libera el lock automáticamente tras 30 min
    //   (cae muerta la instancia). lockAtLeastFor = 1000ms: evita ejecuciones
    //   solapadas en la misma instancia.
    // NOTA: Sin @Transactional a nivel de método a propósito.
    // Cada préstamo vencido se procesa individualmente y Spring Data JPA
    // ya provee @Transactional en save() y findBy*() desde SimpleJpaRepository.
    // Si un préstamo individual falla, los demás no se ven afectados
    // (evita rollback total que dejaría el servicio inconsistente).
    // ShedLock garantiza que solo una instancia ejecute este scheduler
    // a la vez, y @Version en Prestamo previene doble procesamiento.
    @Observed(name = "elending.cerrarPrestamosVencidos")
    @Scheduled(fixedRate = 3600000)
    @SchedulerLock(name = "prestamos-vencidos", lockAtMostFor = "30m", lockAtLeastFor = "1s")
    public void cerrarPrestamosVencidos() {
        LocalDateTime ahora = LocalDateTime.now();
        log.info("Scheduler ejecutado — verificando préstamos vencidos a las {}", ahora);

        List<Prestamo> vencidos = prestamoRepository
                .findByEstadoAndFechaVencimientoBefore(EstadoPrestamo.ACTIVO, ahora);

        log.info("Préstamos vencidos encontrados: {}", vencidos.size());

        for (Prestamo prestamo : vencidos) {
            try {
                licenseClient.devolver(prestamo.getLibroId());
                prestamo.setEstado(EstadoPrestamo.VENCIDO);
                prestamoRepository.save(prestamo);

                log.info("Préstamo cerrado — id: {}, usuario: {}, libro: {}",
                        prestamo.getId(), prestamo.getUsuarioId(), prestamo.getLibroId());

                try {
                    notificacionPublisher.publicarEvento(
                        NotificacionEvent.prestamoVencido(
                            prestamo.getUsuarioId(), prestamo.getLibroId()));
                } catch (Exception e) {
                    log.warn("No se pudo publicar evento de vencimiento — préstamo {}: {}",
                            prestamo.getId(), e.getMessage());
                }

            } catch (ObjectOptimisticLockingFailureException e) {
                // @Version cambió — otro scheduler o request cerró este préstamo primero
                log.warn("Conflicto de concurrencia al cerrar préstamo id: {} — " +
                        "se procesará en el próximo ciclo del scheduler", prestamo.getId());
            } catch (Exception e) {
                log.error("Error al cerrar préstamo id: {} — {}", 
                        prestamo.getId(), e.getMessage());
            }
        }

        // Avisar próximos a vencer
        LocalDateTime en2Dias = ahora.plusDays(2);
        List<Prestamo> proximosAVencer = prestamoRepository
                .findByEstadoAndFechaVencimientoBefore(EstadoPrestamo.ACTIVO, en2Dias)
                .stream()
                .filter(p -> p.getFechaVencimiento().isAfter(ahora))
                .collect(Collectors.toList());

        log.info("Préstamos próximos a vencer (2 días): {}", proximosAVencer.size());

        for (Prestamo prestamo : proximosAVencer) {
            try {
                notificacionPublisher.publicarEvento(
                    NotificacionEvent.proximoVencer(
                        prestamo.getUsuarioId(), prestamo.getLibroId()));
            } catch (Exception e) {
                log.warn("No se pudo publicar evento de próximo vencimiento — préstamo {}: {}",
                        prestamo.getId(), e.getMessage());
            }
        }
    }

    private PrestamoResponseDTO mapearADto(Prestamo prestamo) {
        PrestamoResponseDTO dto = new PrestamoResponseDTO();
        dto.setId(prestamo.getId());
        dto.setUsuarioId(prestamo.getUsuarioId());
        dto.setLibroId(prestamo.getLibroId());
        dto.setFechaInicio(prestamo.getFechaInicio());
        dto.setFechaVencimiento(prestamo.getFechaVencimiento());
        dto.setEstado(prestamo.getEstado());
        return dto;
    }
}