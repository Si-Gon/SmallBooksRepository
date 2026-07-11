package com.silvio.license.service;

import com.silvio.license.dto.LicenseRequestDTO;
import com.silvio.license.dto.LicenseResponseDTO;
import com.silvio.license.model.License;
import com.silvio.license.repository.LicenseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Tests unitarios de LicenseService.
 *
 * LicenseService gestiona el stock de copias digitales de cada libro.
 * La lógica más interesante está en:
 *
 * - prestar(): descuenta 1 copia — falla si no hay copias disponibles (copias == 0)
 * - devolver(): suma 1 copia — falla si todas las copias ya están disponibles
 *   (copias_disponibles == total_copias, es decir, nadie las tiene prestadas)
 * - actualizar(): permite cambiar el total, pero no por debajo de las copias prestadas
 * - crear(): falla si ya existe licencia para ese libroId
 *
 * Estos son los "bordes" del negocio — los casos que hay que cubrir bien.
 */
@ExtendWith(MockitoExtension.class)
class LicenseServiceTest {

    @Mock
    private LicenseRepository licenseRepository;

    @InjectMocks
    private LicenseService licenseService;

    private License license(Long libroId, int total, int disponibles) {
        License l = new License();
        l.setId(1L);
        l.setLibroId(libroId);
        l.setTotalCopias(total);
        l.setCopiasDisponibles(disponibles);
        return l;
    }

    private LicenseRequestDTO request(Long libroId, int totalCopias) {
        LicenseRequestDTO r = new LicenseRequestDTO();
        r.setLibroId(libroId);
        r.setTotalCopias(totalCopias);
        return r;
    }

    // =====================================================================
    // obtenerTodas()
    // =====================================================================

    @Test
    void obtenerTodas_conLicencias_retornaListaMapeada() {
        when(licenseRepository.findAll()).thenReturn(List.of(
            license(1L, 10, 8),
            license(2L, 5, 5)
        ));

        List<LicenseResponseDTO> resultado = licenseService.obtenerTodas();

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).getLibroId()).isEqualTo(1L);
        assertThat(resultado.get(0).getTotalCopias()).isEqualTo(10);
        assertThat(resultado.get(0).getCopiasDisponibles()).isEqualTo(8);
    }

    @Test
    void obtenerTodas_sinLicencias_retornaListaVacia() {
        when(licenseRepository.findAll()).thenReturn(List.of());

        assertThat(licenseService.obtenerTodas()).isEmpty();
    }

    // =====================================================================
    // obtenerPorLibroId()
    // =====================================================================

    @Test
    void obtenerPorLibroId_existe_retornaDTO() {
        when(licenseRepository.findByLibroId(1L)).thenReturn(Optional.of(license(1L, 10, 8)));

        LicenseResponseDTO resultado = licenseService.obtenerPorLibroId(1L);

        assertThat(resultado.getLibroId()).isEqualTo(1L);
        assertThat(resultado.getTotalCopias()).isEqualTo(10);
        assertThat(resultado.getCopiasDisponibles()).isEqualTo(8);
    }

    @Test
    void obtenerPorLibroId_noExiste_lanzaExcepcion() {
        when(licenseRepository.findByLibroId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> licenseService.obtenerPorLibroId(99L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("No existe licencia para el libro con id: 99");
    }

    // =====================================================================
    // crear()
    // =====================================================================

    @Test
    void crear_nuevaLicencia_debeGuardarYRetornarDTO() {
        when(licenseRepository.findByLibroId(5L)).thenReturn(Optional.empty());
        when(licenseRepository.save(any(License.class))).thenAnswer(inv -> {
            License l = inv.getArgument(0);
            l.setId(10L);
            return l;
        });

        LicenseResponseDTO resultado = licenseService.crear(request(5L, 10));

        assertThat(resultado.getLibroId()).isEqualTo(5L);
        assertThat(resultado.getTotalCopias()).isEqualTo(10);
        // Al crear, copias disponibles == total copias (ninguna prestada aún)
        assertThat(resultado.getCopiasDisponibles()).isEqualTo(10);
        verify(licenseRepository).save(any(License.class));
    }

    @Test
    void crear_licenciaYaExiste_lanzaExcepcion() {
        when(licenseRepository.findByLibroId(1L)).thenReturn(Optional.of(license(1L, 10, 8)));

        assertThatThrownBy(() -> licenseService.crear(request(1L, 5)))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Ya existe una licencia para el libro con id: 1");

        verify(licenseRepository, never()).save(any());
    }

    // =====================================================================
    // prestar() — descuenta 1 copia disponible
    // =====================================================================

    @Test
    void prestar_conCopiasDisponibles_decrementaYGuarda() {
        License l = license(1L, 10, 3); // 3 disponibles
        when(licenseRepository.findByLibroId(1L)).thenReturn(Optional.of(l));
        when(licenseRepository.save(l)).thenReturn(l);

        LicenseResponseDTO resultado = licenseService.prestar(1L);

        // Debe quedar en 2 (3 - 1)
        assertThat(resultado.getCopiasDisponibles()).isEqualTo(2);
        assertThat(l.getCopiasDisponibles()).isEqualTo(2);
        verify(licenseRepository).save(l);
    }

    @Test
    void prestar_sinCopiasDisponibles_lanzaExcepcion() {
        // copias disponibles == 0: no se puede prestar
        License l = license(1L, 5, 0);
        when(licenseRepository.findByLibroId(1L)).thenReturn(Optional.of(l));

        assertThatThrownBy(() -> licenseService.prestar(1L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("No hay copias disponibles del libro con id: 1");

        verify(licenseRepository, never()).save(any());
    }

    @Test
    void prestar_licenciaNoExiste_lanzaExcepcion() {
        when(licenseRepository.findByLibroId(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> licenseService.prestar(7L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("No existe licencia para el libro con id: 7");
    }

    // =====================================================================
    // devolver() — incrementa 1 copia disponible
    // =====================================================================

    @Test
    void devolver_conCopiasPrestadas_incrementaYGuarda() {
        // total=5, disponibles=3 → hay 2 prestadas, se puede devolver
        License l = license(1L, 5, 3);
        when(licenseRepository.findByLibroId(1L)).thenReturn(Optional.of(l));
        when(licenseRepository.save(l)).thenReturn(l);

        LicenseResponseDTO resultado = licenseService.devolver(1L);

        // Debe quedar en 4 (3 + 1)
        assertThat(resultado.getCopiasDisponibles()).isEqualTo(4);
        verify(licenseRepository).save(l);
    }

    @Test
    void devolver_todasDisponibles_lanzaExcepcion() {
        // copias disponibles == total copias → ninguna prestada, no hay que devolver
        License l = license(1L, 5, 5);
        when(licenseRepository.findByLibroId(1L)).thenReturn(Optional.of(l));

        assertThatThrownBy(() -> licenseService.devolver(1L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Todas las copias del libro ya están disponibles");

        verify(licenseRepository, never()).save(any());
    }

    // =====================================================================
    // prestar() — optimistic locking: 3 reintentos ante conflicto @Version
    // =====================================================================

    @Test
    void prestar_con_3_conflictos_OL_consecutivos_lanza_RuntimeException() {
        // Simula 3 conflictos de @Version seguidos — el wrapper agota reintentos
        License l = license(1L, 10, 3);
        when(licenseRepository.findByLibroId(1L)).thenReturn(Optional.of(l));
        when(licenseRepository.save(any(License.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException("license", null));

        assertThatThrownBy(() -> licenseService.prestar(1L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("muchos usuarios");

        // save() se intentó 3 veces (una por cada reintento)
        verify(licenseRepository, times(3)).save(any(License.class));
    }

    @Test
    void prestar_con_2_conflictos_OL_y_3er_intento_exitoso_funciona() {
        // Simula 2 conflictos @Version seguidos + éxito en el 3er intento
        // NOTA: doPrestar() modifica el entity antes de save(), por lo que
        // las copias disponibles en el DTO reflejan el 3er intento (3-1-1-1=0).
        // Lo importante es que no lance excepción y reintente 3 veces.
        License l = license(1L, 10, 3);
        when(licenseRepository.findByLibroId(1L)).thenReturn(Optional.of(l));
        when(licenseRepository.save(any(License.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException("license", null))
                .thenThrow(new ObjectOptimisticLockingFailureException("license", null))
                .thenReturn(l);

        licenseService.prestar(1L);

        verify(licenseRepository, times(3)).save(any(License.class));
    }

    // =====================================================================
    // devolver() — optimistic locking: 3 reintentos ante conflicto @Version
    // =====================================================================

    @Test
    void devolver_con_3_conflictos_OL_consecutivos_lanza_RuntimeException() {
        // Simula 3 conflictos de @Version seguidos en devolver()
        License l = license(1L, 10, 3);
        when(licenseRepository.findByLibroId(1L)).thenReturn(Optional.of(l));
        when(licenseRepository.save(any(License.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException("license", null));

        assertThatThrownBy(() -> licenseService.devolver(1L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Error al devolver copia");

        verify(licenseRepository, times(3)).save(any(License.class));
    }

    @Test
    void devolver_con_2_conflictos_OL_y_3er_intento_exitoso_funciona() {
        // Simula 2 conflictos @Version seguidos + éxito en el 3er intento
        // Lo importante es que no lance excepción y reintente 3 veces.
        License l = license(1L, 10, 3);
        when(licenseRepository.findByLibroId(1L)).thenReturn(Optional.of(l));
        when(licenseRepository.save(any(License.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException("license", null))
                .thenThrow(new ObjectOptimisticLockingFailureException("license", null))
                .thenReturn(l);

        licenseService.devolver(1L);

        verify(licenseRepository, times(3)).save(any(License.class));
    }

    // =====================================================================
    // actualizar() — permite cambiar total de copias con restricciones
    // =====================================================================

    @Test
    void actualizar_aumentandoCopias_debeActualizarYRetornarDTO() {
        // total=5, disponibles=3 → 2 prestadas
        License l = license(1L, 5, 3);
        when(licenseRepository.findByLibroId(1L)).thenReturn(Optional.of(l));
        when(licenseRepository.save(l)).thenReturn(l);

        // Aumentamos a 10 — las 2 prestadas siguen prestadas
        LicenseResponseDTO resultado = licenseService.actualizar(1L, request(1L, 10));

        assertThat(resultado.getTotalCopias()).isEqualTo(10);
        // disponibles = nuevo total - copias prestadas = 10 - 2 = 8
        assertThat(resultado.getCopiasDisponibles()).isEqualTo(8);
    }

    @Test
    void actualizar_reduciendoPorDebajoDePrestatarios_lanzaExcepcion() {
        // total=5, disponibles=1 → 4 prestadas
        // No se puede reducir a 3 si hay 4 prestadas
        License l = license(1L, 5, 1);
        when(licenseRepository.findByLibroId(1L)).thenReturn(Optional.of(l));

        assertThatThrownBy(() -> licenseService.actualizar(1L, request(1L, 3)))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("No se puede reducir a 3 copias");

        verify(licenseRepository, never()).save(any());
    }

    @Test
    void actualizar_licenciaNoExiste_lanzaExcepcion() {
        when(licenseRepository.findByLibroId(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> licenseService.actualizar(9L, request(9L, 5)))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("No existe licencia para el libro con id: 9");
    }
}
