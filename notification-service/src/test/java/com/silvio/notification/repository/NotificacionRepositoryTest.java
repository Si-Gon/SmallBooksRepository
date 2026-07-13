package com.silvio.notification.repository;

import com.silvio.notification.model.Notificacion;
import com.silvio.notification.model.Notificacion.TipoNotificacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
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
}
