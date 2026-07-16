package com.silvio.notification.repository;

import com.silvio.notification.model.Notificacion;
import com.silvio.notification.model.Notificacion.TipoNotificacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests de integración de NotificacionRepository con H2.
 *
 * Verifica que la constraint UNIQUE de idempotencyKey funcione
 * a nivel de base de datos, complementando los tests unitarios
 * del servicio que usan mocks.
 */
@DataJpaTest
@ActiveProfiles("test")
class NotificacionRepositoryTest {

    @Autowired
    private NotificacionRepository notificacionRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Notificacion notificacionBase(String idempotencyKey) {
        Notificacion n = new Notificacion();
        n.setUsuarioId("silvio");
        n.setTipo(TipoNotificacion.PRESTAMO_CREADO);
        n.setMensaje("Tu préstamo fue creado exitosamente");
        n.setFechaEnvio(LocalDateTime.now());
        n.setLeida(false);
        n.setIdempotencyKey(idempotencyKey);
        return n;
    }

    @BeforeEach
    void setUp() {
        notificacionRepository.deleteAll();
    }

    // ─── findByIdempotencyKey ─────────────────────────────────────────────────

    @Test
    void findByIdempotencyKey_retornaNotificacion_cuandoExiste() {
        // Given
        Notificacion guardada = notificacionRepository.save(
                notificacionBase("key-123-unica"));

        // When
        Optional<Notificacion> encontrada = notificacionRepository
                .findByIdempotencyKey("key-123-unica");

        // Then
        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().getId()).isEqualTo(guardada.getId());
        assertThat(encontrada.get().getUsuarioId()).isEqualTo("silvio");
        assertThat(encontrada.get().getIdempotencyKey()).isEqualTo("key-123-unica");
    }

    @Test
    void findByIdempotencyKey_retornaVacio_cuandoNoExiste() {
        // When
        Optional<Notificacion> encontrada = notificacionRepository
                .findByIdempotencyKey("key-inexistente");

        // Then
        assertThat(encontrada).isEmpty();
    }

    // ─── existsByIdempotencyKey ──────────────────────────────────────────────

    @Test
    void existsByIdempotencyKey_retornaTrue_cuandoExiste() {
        // Given
        notificacionRepository.save(notificacionBase("key-existe-test"));

        // When
        boolean existe = notificacionRepository
                .existsByIdempotencyKey("key-existe-test");

        // Then
        assertThat(existe).isTrue();
    }

    @Test
    void existsByIdempotencyKey_retornaFalse_cuandoNoExiste() {
        // When
        boolean existe = notificacionRepository
                .existsByIdempotencyKey("key-no-existe");

        // Then
        assertThat(existe).isFalse();
    }

    // ─── Unique Constraint ──────────────────────────────────────────────────

    @Test
    void guardarConIdempotencyKeyDuplicado_lanzaDataIntegrityViolation() {
        // Given — primera notificación guardada exitosamente
        notificacionRepository.save(notificacionBase("key-duplicada"));

        // When — segunda notificación con misma key
        Notificacion duplicado = notificacionBase("key-duplicada");
        duplicado.setUsuarioId("otro-usuario");
        duplicado.setMensaje("Mensaje diferente");

        // Then — debe lanzar excepción por violación de UNIQUE INDEX
        assertThatThrownBy(() -> notificacionRepository.save(duplicado))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void guardarConIdempotencyKeyDiferente_noLanzaExcepcion() {
        // Given
        notificacionRepository.save(notificacionBase("key-1"));

        // When — mensaje con key diferente
        Notificacion otro = notificacionBase("key-2");
        otro.setUsuarioId("ana");
        otro.setMensaje("Notificación diferente");

        // Then — no debe lanzar excepción
        assertThatCode(() -> notificacionRepository.save(otro))
                .doesNotThrowAnyException();
    }

    // ─── consultas existentes (regresión) ────────────────────────────────────

    @Test
    void findByUsuarioIdOrderByFechaEnvioDesc_funciona_conIdempotencyKey() {
        // Given
        notificacionRepository.save(notificacionBase("key-a"));
        notificacionRepository.save(notificacionBase("key-b"));
        notificacionRepository.save(notificacionBase("key-c"));

        // When
        var resultado = notificacionRepository
                .findByUsuarioIdOrderByFechaEnvioDesc("silvio");

        // Then — las consultas existentes siguen funcionando
        assertThat(resultado).hasSize(3);
        assertThat(resultado.get(0).getFechaEnvio())
                .isAfterOrEqualTo(resultado.get(1).getFechaEnvio());
    }

    @Test
    void findByUsuarioIdAndLeidaFalse_funciona_conIdempotencyKey() {
        // Given
        Notificacion leida = notificacionBase("key-leida");
        leida.setLeida(true);
        notificacionRepository.save(leida);
        notificacionRepository.save(notificacionBase("key-no-leida"));

        // When
        var resultado = notificacionRepository
                .findByUsuarioIdAndLeidaFalse("silvio");

        // Then — solo retorna las no leídas
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getIdempotencyKey()).isEqualTo("key-no-leida");
        assertThat(resultado.get(0).getLeida()).isFalse();
    }

    // ─── marcarTodasLeidasPorUsuario (bulk update) ────────────────────────────

    @Test
    void marcarTodasLeidasPorUsuario_conNoLeidas_marcaTodasConUnSoloUpdate() {
        // Given — 3 notificaciones, 2 no leídas y 1 ya leída
        notificacionRepository.save(notificacionBase("key-bulk-a"));    // leida=false
        Notificacion leida = notificacionBase("key-bulk-b");
        leida.setLeida(true);
        notificacionRepository.save(leida);
        notificacionRepository.save(notificacionBase("key-bulk-c"));    // leida=false

        // When — bulk update
        int actualizadas = notificacionRepository.marcarTodasLeidasPorUsuario("silvio");

        // Then — solo las 2 no leídas fueron afectadas
        assertThat(actualizadas).isEqualTo(2);

        // Verificar que todas las notificaciones del usuario quedaron como leídas
        List<Notificacion> todas = notificacionRepository
                .findByUsuarioIdOrderByFechaEnvioDesc("silvio");
        assertThat(todas).hasSize(3);
        assertThat(todas).allMatch(Notificacion::getLeida);
    }

    @Test
    void marcarTodasLeidasPorUsuario_sinNoLeidas_retornaCero() {
        // Given — todas las notificaciones ya están leídas
        Notificacion n1 = notificacionBase("key-ya-leida-1");
        n1.setLeida(true);
        notificacionRepository.save(n1);
        Notificacion n2 = notificacionBase("key-ya-leida-2");
        n2.setLeida(true);
        notificacionRepository.save(n2);

        // When
        int actualizadas = notificacionRepository.marcarTodasLeidasPorUsuario("silvio");

        // Then — ninguna fila afectada porque todas ya estaban leídas
        assertThat(actualizadas).isZero();
    }

    @Test
    void marcarTodasLeidasPorUsuario_sinNotificaciones_retornaCero() {
        // When — usuario sin ninguna notificación en BD
        int actualizadas = notificacionRepository
                .marcarTodasLeidasPorUsuario("usuario-sin-notificaciones");

        // Then
        assertThat(actualizadas).isZero();
    }

    @Test
    void marcarTodasLeidasPorUsuario_soloAfectaAlUsuarioEspecificado() {
        // Given — 2 usuarios con notificaciones no leídas
        Notificacion n1 = notificacionBase("key-user1-1");
        n1.setUsuarioId("usuario1");
        notificacionRepository.save(n1);
        Notificacion n2 = notificacionBase("key-user2-1");
        n2.setUsuarioId("usuario2");
        notificacionRepository.save(n2);

        // When — marcar solo para usuario1
        int actualizadas = notificacionRepository
                .marcarTodasLeidasPorUsuario("usuario1");

        // Then
        assertThat(actualizadas).isEqualTo(1);

        // usuario1 ya no tiene no leídas
        List<Notificacion> noLeidasU1 = notificacionRepository
                .findByUsuarioIdAndLeidaFalse("usuario1");
        assertThat(noLeidasU1).isEmpty();

        // usuario2 todavía tiene su notificación no leída
        List<Notificacion> noLeidasU2 = notificacionRepository
                .findByUsuarioIdAndLeidaFalse("usuario2");
        assertThat(noLeidasU2).hasSize(1);
        assertThat(noLeidasU2.get(0).getLeida()).isFalse();
    }

    @Test
    void marcarTodasLeidasPorUsuario_clearAutomatically_evitaCacheObsoleto() {
        // Given — guardar notificaciones y limpiar el contexto de persistencia
        notificacionRepository.save(notificacionBase("key-clear-1"));
        notificacionRepository.save(notificacionBase("key-clear-2"));
        entityManager.flush();
        entityManager.clear(); // partir con contexto vacío

        // Precondición: desde contexto fresco, hay 2 no leídas
        List<Notificacion> antes = notificacionRepository
                .findByUsuarioIdAndLeidaFalse("silvio");
        assertThat(antes).hasSize(2);

        // When — bulk update (clearAutomatically=true limpia el contexto post-update)
        int actualizadas = notificacionRepository
                .marcarTodasLeidasPorUsuario("silvio");
        assertThat(actualizadas).isEqualTo(2);

        // Then — la cache del contexto se limpió y la consulta refleja el estado real
        List<Notificacion> despues = notificacionRepository
                .findByUsuarioIdAndLeidaFalse("silvio");
        assertThat(despues).isEmpty();
    }
}
