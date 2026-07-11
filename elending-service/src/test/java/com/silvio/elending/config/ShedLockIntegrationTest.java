package com.silvio.elending.config;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de integración para ShedLock.
 * Verifica que el LockProvider JDBC funcione correctamente con H2
 * y que el bloqueo distribuido evite ejecuciones concurrentes
 * del scheduler entre múltiples instancias.
 *
 * NOTA: Flyway está deshabilitado en test profile, por lo que la tabla
 * shedlock se crea manualmente en @BeforeEach.
 * En producción la crea la migración V3__agregar_tabla_shedlock.sql.
 */
@SpringBootTest
@ActiveProfiles("test")
class ShedLockIntegrationTest {

    @Autowired
    private LockProvider lockProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // Crear la tabla shedlock ya que Flyway está deshabilitado en test
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS shedlock (
                name       VARCHAR(64)  NOT NULL PRIMARY KEY,
                lock_until TIMESTAMP    NOT NULL,
                locked_at  TIMESTAMP    NOT NULL,
                locked_by  VARCHAR(255) NOT NULL
            )
        """);
        // Limpiar locks de tests anteriores
        jdbcTemplate.execute("DELETE FROM shedlock");
    }

    @Test
    void contextLoads_conShedLockYH2() {
        // Verifica que @EnableSchedulerLock + @EnableScheduling + LockProvider
        // no interfieran al levantar el contexto con H2 (flyway deshabilitado)
        assertNotNull(lockProvider, "LockProvider debe estar disponible en el contexto");
    }

    @Test
    void lockProvider_adquiereLockExitosamente() {
        // Given — nombre de lock único
        Instant ahora = Instant.now();
        LockConfiguration config = new LockConfiguration(
                ahora, "test-lock-adquirir",
                Duration.ofSeconds(30), Duration.ZERO);

        // When — adquirir lock
        Optional<SimpleLock> lock = lockProvider.lock(config);

        // Then
        assertTrue(lock.isPresent(), "El lock debe adquirirse exitosamente");

        // Verificar que existe un registro en la tabla shedlock
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shedlock WHERE name = ?",
                Integer.class, "test-lock-adquirir");
        assertEquals(1, count, "Debe haber un registro en la tabla shedlock");
    }

    @Test
    void lockProvider_lockYaTomado_noAdquiereSegundoLock() {
        // Given — adquirir el primer lock
        Instant ahora = Instant.now();
        LockConfiguration config = new LockConfiguration(
                ahora, "test-lock-duplicado",
                Duration.ofSeconds(30), Duration.ZERO);

        Optional<SimpleLock> primerLock = lockProvider.lock(config);
        assertTrue(primerLock.isPresent(), "Primer lock debe adquirirse");

        // When — intentar adquirir el mismo lock
        Optional<SimpleLock> segundoLock = lockProvider.lock(config);

        // Then — el segundo intento debe fallar
        assertFalse(segundoLock.isPresent(),
                "No debe adquirir un lock que ya está tomado");
    }

    @Test
    void lockProvider_lockSeLiberaAlDesbloquear() {
        // Given — adquirir lock
        Instant ahora = Instant.now();
        LockConfiguration config = new LockConfiguration(
                ahora, "test-lock-liberar",
                Duration.ofSeconds(30), Duration.ZERO);

        Optional<SimpleLock> lock = lockProvider.lock(config);
        assertTrue(lock.isPresent(), "Lock debe adquirirse");

        // When — liberar el lock explícitamente
        lock.get().unlock();

        // Then — se puede adquirir de nuevo
        Optional<SimpleLock> lockRenovado = lockProvider.lock(config);
        assertTrue(lockRenovado.isPresent(),
                "El lock debe poder adquirirse nuevamente tras liberarlo");
        lockRenovado.get().unlock();
    }

    @Test
    void lockProvider_lockExpirado_permiteNuevoLock() throws InterruptedException {
        // Given — lock con expiración muy corta (1 segundo)
        Instant ahora = Instant.now();
        LockConfiguration config = new LockConfiguration(
                ahora, "test-lock-expiracion",
                Duration.ofSeconds(1), Duration.ZERO); // expira en 1s

        Optional<SimpleLock> lock = lockProvider.lock(config);
        assertTrue(lock.isPresent(), "Lock debe adquirirse");

        // NO liberar el lock — esperar a que expire
        Thread.sleep(1500); // 1.5s > lockAtMostFor de 1s

        // When — intentar adquirir el mismo lock ya expirado
        Optional<SimpleLock> lockExpirado = lockProvider.lock(config);

        // Then — debe adquirirse porque el lock anterior expiró
        assertTrue(lockExpirado.isPresent(),
                "Debe adquirir el lock porque el anterior expiró");
        lockExpirado.get().unlock();
    }

    @Test
    void lockProvider_locksConNombresDiferentes_noBloqueanEntreSi() {
        // Given — dos nombres de lock diferentes
        Instant ahora = Instant.now();
        LockConfiguration config1 = new LockConfiguration(
                ahora, "test-lock-1",
                Duration.ofSeconds(30), Duration.ZERO);
        LockConfiguration config2 = new LockConfiguration(
                ahora, "test-lock-2",
                Duration.ofSeconds(30), Duration.ZERO);

        // When — adquirir ambos
        Optional<SimpleLock> lock1 = lockProvider.lock(config1);
        Optional<SimpleLock> lock2 = lockProvider.lock(config2);

        // Then — ambos deben adquirirse (son independientes)
        assertTrue(lock1.isPresent(), "Lock 1 debe adquirirse");
        assertTrue(lock2.isPresent(), "Lock 2 debe adquirirse independientemente");

        lock1.get().unlock();
        lock2.get().unlock();
    }

    @Test
    void lockProvider_concurrencia_soloUnHiloObtieneLock() throws InterruptedException {
        // Given — 5 hilos compiten por el mismo lock de scheduler
        String lockName = "test-lock-concurrente";
        int numHilos = 5;

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger locksAdquiridos = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(numHilos);

        for (int i = 0; i < numHilos; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    LockConfiguration config = new LockConfiguration(
                            Instant.now(), lockName,
                            Duration.ofSeconds(30), Duration.ZERO);
                    Optional<SimpleLock> lock = lockProvider.lock(config);
                    if (lock.isPresent()) {
                        locksAdquiridos.incrementAndGet();
                        // No liberar — simula que el scheduler está ejecutándose
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        latch.countDown();

        executor.shutdown();
        if (!executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }

        // Then — solo 1 hilo debe adquirir el lock
        assertEquals(1, locksAdquiridos.get(),
                "Solo un hilo debe adquirir el lock concurrente");
    }

    @Test
    void lockProvider_lockPrestamosVencidos_tieneNombreCorrecto() {
        // Verifica que el nombre del lock coincida con @SchedulerLock(name = "prestamos-vencidos")
        Instant ahora = Instant.now();
        LockConfiguration config = new LockConfiguration(
                ahora, "prestamos-vencidos",
                Duration.ofSeconds(30), Duration.ZERO);

        Optional<SimpleLock> lock = lockProvider.lock(config);

        assertTrue(lock.isPresent(),
                "El lock 'prestamos-vencidos' debe poder adquirirse");
        assertEquals("prestamos-vencidos",
                jdbcTemplate.queryForObject(
                        "SELECT name FROM shedlock WHERE name = ?",
                        String.class, "prestamos-vencidos"),
                "El nombre del lock en BD debe coincidir");
        lock.get().unlock();
    }
}
