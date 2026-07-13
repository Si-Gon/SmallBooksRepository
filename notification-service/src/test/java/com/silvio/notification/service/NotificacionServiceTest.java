package com.silvio.notification.service;

import com.silvio.notification.dto.NotificacionDTO;
import com.silvio.notification.dto.NotificacionRequestDTO;
import com.silvio.notification.model.Notificacion;
import com.silvio.notification.model.Notificacion.TipoNotificacion;
import com.silvio.notification.repository.NotificacionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de NotificacionService.
 *
 * El servicio tiene 4 operaciones:
 * - crear(): guarda una nueva notificación con leida=false y fechaEnvio=now
 *   e incluye idempotencyKey (SHA-256) para evitar duplicados por reintentos
 * - obtenerPorUsuario(): devuelve todas ordenadas por fecha DESC
 * - obtenerNoLeidas(): filtra solo las no leídas
 * - marcarLeida(): pone leida=true para una notificación específica
 * - marcarTodasLeidas(): itera y pone leida=true en todas las no leídas del usuario
 */
@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

    @Mock
    private NotificacionRepository notificacionRepository;

    @InjectMocks
    private NotificacionService notificacionService;

    @Captor
    private ArgumentCaptor<Notificacion> notificacionCaptor;

    private Notificacion entity(Long id, String usuarioId, TipoNotificacion tipo, boolean leida) {
        Notificacion n = new Notificacion();
        n.setId(id);
        n.setUsuarioId(usuarioId);
        n.setTipo(tipo);
        n.setMensaje("Mensaje de prueba tipo " + tipo);
        n.setFechaEnvio(LocalDateTime.now());
        n.setLeida(leida);
        return n;
    }

    private NotificacionRequestDTO request(String usuarioId, TipoNotificacion tipo) {
        return requestConMensaje(usuarioId, tipo, "Mensaje de prueba tipo " + tipo);
    }

    private NotificacionRequestDTO requestConMensaje(String usuarioId, TipoNotificacion tipo, String mensaje) {
        NotificacionRequestDTO r = new NotificacionRequestDTO();
        r.setUsuarioId(usuarioId);
        r.setTipo(tipo);
        r.setMensaje(mensaje);
        return r;
    }

    // =====================================================================
    // crear()
    // =====================================================================

    @Test
    void crear_debeGuardarConIdempotencyKeyYRetornarDTO() {
        Notificacion guardada = entity(1L, "silvio", TipoNotificacion.PRESTAMO_CREADO, false);
        when(notificacionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(notificacionRepository.save(any(Notificacion.class))).thenReturn(guardada);

        NotificacionDTO resultado = notificacionService.crear(
            request("silvio", TipoNotificacion.PRESTAMO_CREADO));

        assertThat(resultado.getUsuarioId()).isEqualTo("silvio");
        assertThat(resultado.getTipo()).isEqualTo(TipoNotificacion.PRESTAMO_CREADO);
        assertThat(resultado.getLeida()).isFalse();

        // Verifica que se llamó a findByIdempotencyKey (fast-path) y luego a save
        verify(notificacionRepository).findByIdempotencyKey(anyString());
        verify(notificacionRepository).save(notificacionCaptor.capture());

        // Verifica que la entidad guardada tiene el idempotencyKey generado
        Notificacion entidadGuardada = notificacionCaptor.getValue();
        assertThat(entidadGuardada.getIdempotencyKey()).isNotNull();
        assertThat(entidadGuardada.getIdempotencyKey()).hasSize(64); // SHA-256 hex
    }

    @Test
    void crear_tipoProximoVencer_debeGuardarCorrectamente() {
        Notificacion guardada = entity(2L, "silvio", TipoNotificacion.PROXIMO_VENCER, false);
        when(notificacionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(notificacionRepository.save(any())).thenReturn(guardada);

        NotificacionDTO resultado = notificacionService.crear(
            request("silvio", TipoNotificacion.PROXIMO_VENCER));

        assertThat(resultado.getTipo()).isEqualTo(TipoNotificacion.PROXIMO_VENCER);
    }

    // =====================================================================
    // crear() — idempotencia: duplicados
    // =====================================================================

    @Test
    void crear_cuandoYaExisteIdempotencyKey_retornaExistenteSinGuardar() {
        // Given — misma request genera mismo idempotencyKey
        Notificacion existente = entity(5L, "silvio", TipoNotificacion.PRESTAMO_CREADO, false);
        when(notificacionRepository.findByIdempotencyKey(anyString()))
                .thenReturn(Optional.of(existente));

        // When — intento duplicado con el mismo contenido
        NotificacionDTO resultado = notificacionService.crear(
            request("silvio", TipoNotificacion.PRESTAMO_CREADO));

        // Then — no debe llamar a save, debe devolver la existente
        assertThat(resultado.getId()).isEqualTo(5L);
        assertThat(resultado.getUsuarioId()).isEqualTo("silvio");
        verify(notificacionRepository, never()).save(any(Notificacion.class));
    }

    @Test
    void crear_requestConMismoContenido_generaMismaIdempotencyKey() {
        // Given — dos requests con idéntico contenido
        Notificacion guardada = entity(1L, "ana", TipoNotificacion.VENCIDO, false);
        when(notificacionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(notificacionRepository.save(any(Notificacion.class))).thenReturn(guardada);

        notificacionService.crear(request("ana", TipoNotificacion.VENCIDO));

        verify(notificacionRepository).save(notificacionCaptor.capture());
        String primeraKey = notificacionCaptor.getValue().getIdempotencyKey();

        // Reset y segunda llamada
        reset(notificacionRepository);
        when(notificacionRepository.findByIdempotencyKey(primeraKey)).thenReturn(Optional.of(guardada));

        NotificacionDTO resultado2 = notificacionService.crear(request("ana", TipoNotificacion.VENCIDO));

        // Then — el duplicado no guarda y retorna la misma notificación
        assertThat(resultado2.getId()).isEqualTo(1L);
        verify(notificacionRepository, never()).save(any(Notificacion.class));
    }

    @Test
    void crear_cuandoDataIntegrityViolation_ignoraYRetornaExistente() {
        // Given — save lanza DataIntegrityViolationException (race condition)
        Notificacion guardada = entity(3L, "juan", TipoNotificacion.PRESTAMO_CREADO, false);
        when(notificacionRepository.findByIdempotencyKey(anyString()))
                .thenReturn(Optional.empty())          // 1ra llamada: no existe
                .thenReturn(Optional.of(guardada));    // 2da llamada: ya existe (otro nodo guardó)
        when(notificacionRepository.save(any(Notificacion.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        // When
        NotificacionDTO resultado = notificacionService.crear(
            request("juan", TipoNotificacion.PRESTAMO_CREADO));

        // Then — recupera el registro del otro nodo
        assertThat(resultado.getId()).isEqualTo(3L);
        assertThat(resultado.getUsuarioId()).isEqualTo("juan");
        verify(notificacionRepository, times(2)).findByIdempotencyKey(anyString());
        verify(notificacionRepository).save(any(Notificacion.class));
    }

    // =====================================================================
    // crear() — idempotencia: casos borde con valores null
    // =====================================================================

    @Test
    void crear_conMensajeNull_generaKeyDeterministicoSinNPE() {
        // Given — mensaje null desde RabbitMQ (el listener crea el DTO sin @Valid)
        NotificacionRequestDTO request = requestConMensaje("silvio", TipoNotificacion.PRESTAMO_CREADO, null);
        Notificacion guardada = entity(1L, "silvio", TipoNotificacion.PRESTAMO_CREADO, false);
        when(notificacionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(notificacionRepository.save(any(Notificacion.class))).thenReturn(guardada);

        // When — no debe lanzar NPE aunque mensaje sea null
        NotificacionDTO resultado = notificacionService.crear(request);

        // Then
        assertThat(resultado.getUsuarioId()).isEqualTo("silvio");
        verify(notificacionRepository).save(notificacionCaptor.capture());
        Notificacion entidad = notificacionCaptor.getValue();
        assertThat(entidad.getIdempotencyKey()).isNotNull();
        assertThat(entidad.getIdempotencyKey()).hasSize(64); // SHA-256 hex

        // Verify mismo contenido (incluyendo null) genera mismo key
        reset(notificacionRepository);
        NotificacionRequestDTO request2 = requestConMensaje("silvio", TipoNotificacion.PRESTAMO_CREADO, null);
        when(notificacionRepository.findByIdempotencyKey(anyString()))
                .thenReturn(Optional.of(entity(1L, "silvio", TipoNotificacion.PRESTAMO_CREADO, false)));

        notificacionService.crear(request2);
        verify(notificacionRepository, never()).save(any(Notificacion.class));
    }

    @Test
    void crear_conUsuarioIdNull_mensajeNull_generaKeySinNPE() {
        // Given — usuarioId y mensaje null, pero tipo presente (escenario real desde RabbitMQ el tipo siempre es válido)
        NotificacionRequestDTO request = new NotificacionRequestDTO();
        request.setUsuarioId(null);
        request.setTipo(TipoNotificacion.PRESTAMO_CREADO);
        request.setMensaje(null);

        Notificacion guardada = new Notificacion();
        guardada.setId(1L);
        guardada.setUsuarioId(null);
        guardada.setTipo(TipoNotificacion.PRESTAMO_CREADO);
        guardada.setMensaje(null);
        guardada.setFechaEnvio(LocalDateTime.now());
        guardada.setLeida(false);

        when(notificacionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(notificacionRepository.save(any(Notificacion.class))).thenReturn(guardada);

        // When — no debe lanzar NPE aunque usuarioId y mensaje sean null
        NotificacionDTO resultado = notificacionService.crear(request);

        // Then
        assertThat(resultado.getUsuarioId()).isNull();
        assertThat(resultado.getTipo()).isEqualTo(TipoNotificacion.PRESTAMO_CREADO);
        assertThat(resultado.getMensaje()).isNull();
        verify(notificacionRepository).save(notificacionCaptor.capture());
        assertThat(notificacionCaptor.getValue().getIdempotencyKey()).hasSize(64);
    }

    // =====================================================================
    // obtenerPorUsuario()
    // =====================================================================

    @Test
    void obtenerPorUsuario_conNotificaciones_retornaListaMapeada() {
        List<Notificacion> lista = List.of(
            entity(3L, "silvio", TipoNotificacion.VENCIDO, false),
            entity(1L, "silvio", TipoNotificacion.PRESTAMO_CREADO, true)
        );
        when(notificacionRepository.findByUsuarioIdOrderByFechaEnvioDesc("silvio"))
            .thenReturn(lista);

        List<NotificacionDTO> resultado = notificacionService.obtenerPorUsuario("silvio");

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).getTipo()).isEqualTo(TipoNotificacion.VENCIDO);
        assertThat(resultado.get(1).getTipo()).isEqualTo(TipoNotificacion.PRESTAMO_CREADO);
    }

    @Test
    void obtenerPorUsuario_sinNotificaciones_retornaListaVacia() {
        when(notificacionRepository.findByUsuarioIdOrderByFechaEnvioDesc("nuevo"))
            .thenReturn(List.of());

        List<NotificacionDTO> resultado = notificacionService.obtenerPorUsuario("nuevo");

        assertThat(resultado).isEmpty();
    }

    // =====================================================================
    // obtenerNoLeidas()
    // =====================================================================

    @Test
    void obtenerNoLeidas_retornaSoloLasNoLeidas() {
        List<Notificacion> noLeidas = List.of(
            entity(1L, "silvio", TipoNotificacion.PROXIMO_VENCER, false),
            entity(2L, "silvio", TipoNotificacion.VENCIDO, false)
        );
        when(notificacionRepository.findByUsuarioIdAndLeidaFalse("silvio"))
            .thenReturn(noLeidas);

        List<NotificacionDTO> resultado = notificacionService.obtenerNoLeidas("silvio");

        assertThat(resultado).hasSize(2);
        assertThat(resultado).allMatch(d -> !d.getLeida());
    }

    @Test
    void obtenerNoLeidas_todasLeidas_retornaListaVacia() {
        when(notificacionRepository.findByUsuarioIdAndLeidaFalse("silvio"))
            .thenReturn(List.of());

        List<NotificacionDTO> resultado = notificacionService.obtenerNoLeidas("silvio");

        assertThat(resultado).isEmpty();
    }

    // =====================================================================
    // marcarLeida()
    // =====================================================================

    @Test
    void marcarLeida_notificacionExiste_setLeidaTrueYGuarda() {
        Notificacion n = entity(5L, "silvio", TipoNotificacion.VENCIDO, false);
        when(notificacionRepository.findById(5L)).thenReturn(Optional.of(n));
        when(notificacionRepository.save(n)).thenReturn(n);

        NotificacionDTO resultado = notificacionService.marcarLeida(5L);

        // El service pone leida=true antes de guardar
        assertThat(n.getLeida()).isTrue();
        assertThat(resultado.getLeida()).isTrue();
        verify(notificacionRepository).save(n);
    }

    @Test
    void marcarLeida_notificacionNoExiste_lanzaExcepcion() {
        when(notificacionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificacionService.marcarLeida(99L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Notificación no encontrada con id: 99");

        verify(notificacionRepository, never()).save(any());
    }

    // =====================================================================
    // marcarTodasLeidas()
    // =====================================================================

    @Test
    void marcarTodasLeidas_conNoLeidas_marcaTodasYGuarda() {
        List<Notificacion> noLeidas = List.of(
            entity(1L, "silvio", TipoNotificacion.VENCIDO, false),
            entity(2L, "silvio", TipoNotificacion.PROXIMO_VENCER, false)
        );
        when(notificacionRepository.findByUsuarioIdAndLeidaFalse("silvio"))
            .thenReturn(noLeidas);

        notificacionService.marcarTodasLeidas("silvio");

        // Todas deben tener leida=true después de llamar al método
        assertThat(noLeidas).allMatch(n -> n.getLeida());
        // saveAll debe haberse llamado con la lista completa
        verify(notificacionRepository).saveAll(noLeidas);
    }

    @Test
    void marcarTodasLeidas_sinNoLeidas_noGuardaNada() {
        when(notificacionRepository.findByUsuarioIdAndLeidaFalse("silvio"))
            .thenReturn(List.of());

        notificacionService.marcarTodasLeidas("silvio");

        // saveAll se llama con lista vacía — ningún guardado real
        verify(notificacionRepository).saveAll(List.of());
    }
}
