package com.silvio.notification.service;

import com.silvio.notification.dto.NotificacionDTO;
import com.silvio.notification.dto.NotificacionRequestDTO;
import com.silvio.notification.model.Notificacion;
import com.silvio.notification.model.Notificacion.TipoNotificacion;
import com.silvio.notification.repository.NotificacionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de NotificacionService.
 *
 * El servicio tiene 4 operaciones:
 * - crear(): guarda una nueva notificación con leida=false y fechaEnvio=now
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
        NotificacionRequestDTO r = new NotificacionRequestDTO();
        r.setUsuarioId(usuarioId);
        r.setTipo(tipo);
        r.setMensaje("Mensaje de prueba tipo " + tipo);
        return r;
    }

    // =====================================================================
    // crear()
    // =====================================================================

    @Test
    void crear_debeGuardarConLeidaFalseYRetornarDTO() {
        Notificacion guardada = entity(1L, "silvio", TipoNotificacion.PRESTAMO_CREADO, false);
        when(notificacionRepository.save(any(Notificacion.class))).thenReturn(guardada);

        NotificacionDTO resultado = notificacionService.crear(
            request("silvio", TipoNotificacion.PRESTAMO_CREADO));

        assertThat(resultado.getUsuarioId()).isEqualTo("silvio");
        assertThat(resultado.getTipo()).isEqualTo(TipoNotificacion.PRESTAMO_CREADO);
        assertThat(resultado.getLeida()).isFalse();
        // Verifica que se llamó a save con una notificación nueva
        verify(notificacionRepository).save(any(Notificacion.class));
    }

    @Test
    void crear_tipoProximoVencer_debeGuardarCorrectamente() {
        Notificacion guardada = entity(2L, "silvio", TipoNotificacion.PROXIMO_VENCER, false);
        when(notificacionRepository.save(any())).thenReturn(guardada);

        NotificacionDTO resultado = notificacionService.crear(
            request("silvio", TipoNotificacion.PROXIMO_VENCER));

        assertThat(resultado.getTipo()).isEqualTo(TipoNotificacion.PROXIMO_VENCER);
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
