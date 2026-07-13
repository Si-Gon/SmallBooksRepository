package com.silvio.notification.service;

import com.silvio.notification.model.Notificacion;
import com.silvio.notification.model.Notificacion.TipoNotificacion;
import com.silvio.notification.repository.NotificacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de concurrencia para la constraint UNIQUE de idempotencyKey.
 *
 * Simula el escenario donde RabbitMQ entrega el mismo mensaje
 * a múltiples nodos (o al mismo nodo en reintentos) y todos
 * intentan insertar simultáneamente con la misma idempotencyKey.
 *
 * La BD debe garantizar que solo un inserto tenga éxito gracias
 * al UNIQUE INDEX, y los demás deben recibir DataIntegrityViolationException.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ActiveProfiles("test")
class NotificacionServiceConcurrenciaTest {

    @Autowired
    private NotificacionRepository notificacionRepository;

    @BeforeEach
    void setUp() {
        notificacionRepository.deleteAll();
    }

    private Notificacion crearNotificacion(String idempotencyKey, int index) {
        Notificacion n = new Notificacion();
        n.setUsuarioId("usuario-" + index);
        n.setTipo(TipoNotificacion.PRESTAMO_CREADO);
        n.setMensaje("Préstamo creado — libro " + index);
        n.setFechaEnvio(LocalDateTime.now());
        n.setLeida(false);
        n.setIdempotencyKey(idempotencyKey);
        return n;
    }

    @Test
    void guardarConcurrente_mismaKey_soloUnInsertoExitoso() throws Exception {
        // Given — 5 hilos intentando insertar con la misma idempotencyKey
        int numHilos = 5;
        String keyCompartida = "key-concurrente-race-condition";

        CountDownLatch barrera = new CountDownLatch(1);  // para lanzar todos a la vez
        CountDownLatch fin = new CountDownLatch(numHilos);
        ExecutorService executor = Executors.newFixedThreadPool(numHilos);
        AtomicInteger insertosExitosos = new AtomicInteger(0);
        AtomicInteger violaciones = new AtomicInteger(0);

        for (int i = 0; i < numHilos; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    barrera.await();  // esperar señal de inicio simultáneo
                    notificacionRepository.save(
                            crearNotificacion(keyCompartida, index));
                    insertosExitosos.incrementAndGet();
                } catch (DataIntegrityViolationException e) {
                    violaciones.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    fin.countDown();
                }
            });
        }

        // When — lanzar todos los hilos simultáneamente
        barrera.countDown();
        boolean completado = fin.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then — solo un inserto debe tener éxito
        assertThat(completado)
                .as("Todos los hilos deben completar dentro del timeout")
                .isTrue();
        assertThat(insertosExitosos.get())
                .as("Solo un hilo debe insertar exitosamente la notificación")
                .isEqualTo(1);
        assertThat(violaciones.get())
                .as("Los demás hilos deben recibir DataIntegrityViolationException")
                .isEqualTo(numHilos - 1);
        assertThat(notificacionRepository.count())
                .as("La BD debe contener exactamente un registro")
                .isOne();
    }

    @Test
    void guardarConcurrente_distintasKey_todosLosInsertosExitosos() throws Exception {
        // Given — 5 hilos con keys diferentes no deben competir
        int numHilos = 5;

        CountDownLatch barrera = new CountDownLatch(1);
        CountDownLatch fin = new CountDownLatch(numHilos);
        ExecutorService executor = Executors.newFixedThreadPool(numHilos);
        AtomicInteger insertosExitosos = new AtomicInteger(0);

        for (int i = 0; i < numHilos; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    barrera.await();
                    notificacionRepository.save(
                            crearNotificacion("key-" + index, index));
                    insertosExitosos.incrementAndGet();
                } catch (DataIntegrityViolationException e) {
                    // No debería ocurrir con keys diferentes
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    fin.countDown();
                }
            });
        }

        // When
        barrera.countDown();
        boolean completado = fin.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then — todos los insertos deben tener éxito
        assertThat(completado)
                .as("Todos los hilos deben completar dentro del timeout")
                .isTrue();
        assertThat(insertosExitosos.get())
                .as("Todos los hilos deben insertar exitosamente con keys diferentes")
                .isEqualTo(numHilos);
        assertThat(notificacionRepository.count())
                .as("La BD debe contener todos los registros")
                .isEqualTo(numHilos);
    }

    @Test
    void guardarConcurrente_mismaKey_distintosMensajes_igualExitoso() throws Exception {
        // Given — mismo key pero contenido diferente (escenario real de race condition)
        int numHilos = 3;
        String keyCompartida = "key-misma-race";

        CountDownLatch barrera = new CountDownLatch(1);
        CountDownLatch fin = new CountDownLatch(numHilos);
        ExecutorService executor = Executors.newFixedThreadPool(numHilos);
        AtomicInteger insertosExitosos = new AtomicInteger(0);

        for (int i = 0; i < numHilos; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    barrera.await();
                    Notificacion n = crearNotificacion(keyCompartida, index);
                    n.setMensaje("Mensaje desde hilo " + index);
                    n.setUsuarioId("usuario-comun");
                    notificacionRepository.save(n);
                    insertosExitosos.incrementAndGet();
                } catch (DataIntegrityViolationException e) {
                    // Esperado: duplicate key viola UNIQUE INDEX
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    fin.countDown();
                }
            });
        }

        // When
        barrera.countDown();
        boolean completado = fin.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then — solo un inserto exitoso
        assertThat(completado).isTrue();
        assertThat(insertosExitosos.get())
                .as("Solo un hilo debe insertar exitosamente con la misma key")
                .isEqualTo(1);
        assertThat(notificacionRepository.count())
                .as("La BD debe contener solo un registro")
                .isOne();
    }
}
