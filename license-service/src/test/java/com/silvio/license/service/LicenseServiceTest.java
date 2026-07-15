package com.silvio.license.service;

import com.silvio.license.dto.LicenseRequestDTO;
import com.silvio.license.dto.LicenseResponseDTO;
import com.silvio.license.exception.CopiaNoDisponibleException;
import com.silvio.license.exception.ConflictosConcurrenciaException;
import com.silvio.license.exception.DevolucionInvalidaException;
import com.silvio.license.exception.ErrorDevolucionException;
import com.silvio.license.exception.LicenciaDuplicadaException;
import com.silvio.license.exception.LicenciaNotFoundException;
import com.silvio.license.exception.ReduccionCopiasInvalidaException;
import com.silvio.license.model.License;
import com.silvio.license.repository.LicenseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

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
        Page<License> page = new PageImpl<>(List.of(
            license(1L, 10, 8),
            license(2L, 5, 5)
        ));
        when(licenseRepository.findAll(any(Pageable.class))).thenReturn(page);

        Page<LicenseResponseDTO> resultado = licenseService.obtenerTodas(Pageable.unpaged());

        assertThat(resultado).hasSize(2);
        assertThat(resultado.getContent().get(0).getLibroId()).isEqualTo(1L);
        assertThat(resultado.getContent().get(0).getTotalCopias()).isEqualTo(10);
        assertThat(resultado.getContent().get(0).getCopiasDisponibles()).isEqualTo(8);
    }

    @Test
    void obtenerTodas_sinLicencias_retornaListaVacia() {
        Page<License> page = new PageImpl<>(List.of());
        when(licenseRepository.findAll(any(Pageable.class))).thenReturn(page);

        assertThat(licenseService.obtenerTodas(Pageable.unpaged())).isEmpty();
    }

    // =====================================================================
    // obtenerTodas() — paginación con PageRequest
    // =====================================================================

    @Test
    void obtenerTodas_conPageRequest_retornaPaginaConMetadatos() {
        // Given: 10 licencias en total, página de tamaño 3
        Page<License> page = new PageImpl<>(
            List.of(license(1L, 10, 8), license(2L, 5, 5), license(3L, 8, 8)),
            PageRequest.of(0, 3),
            10L
        );
        when(licenseRepository.findAll(any(Pageable.class))).thenReturn(page);

        // When
        Page<LicenseResponseDTO> resultado = licenseService.obtenerTodas(PageRequest.of(0, 3));

        // Then: verificar metadatos de paginación
        assertThat(resultado.getSize()).isEqualTo(3);
        assertThat(resultado.getNumber()).isZero();
        assertThat(resultado.getTotalElements()).isEqualTo(10);
        assertThat(resultado.getTotalPages()).isEqualTo(4);
        assertThat(resultado.isFirst()).isTrue();
        assertThat(resultado.isLast()).isFalse();
        verify(licenseRepository).findAll(any(Pageable.class));
    }

    @Test
    void obtenerTodas_conPageRequestSegundaPagina_retornaPaginaCorrecta() {
        // Given: 7 licencias, segunda página (índice 1) de tamaño 3
        Page<License> page = new PageImpl<>(
            List.of(license(4L, 3, 1), license(5L, 6, 6)),
            PageRequest.of(1, 3),
            7L
        );
        when(licenseRepository.findAll(any(Pageable.class))).thenReturn(page);

        // When
        Page<LicenseResponseDTO> resultado = licenseService.obtenerTodas(PageRequest.of(1, 3));

        // Then
        assertThat(resultado.getContent()).hasSize(2);
        assertThat(resultado.getNumber()).isEqualTo(1);
        assertThat(resultado.getTotalElements()).isEqualTo(7);
        assertThat(resultado.isFirst()).isFalse();
        assertThat(resultado.isLast()).isFalse();
        verify(licenseRepository).findAll(any(Pageable.class));
    }

    @Test
    void obtenerTodas_conSort_retornaPaginaOrdenada() {
        // Given: ordenar por totalCopias descendente
        Pageable pageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "totalCopias"));
        Page<License> page = new PageImpl<>(List.of(license(1L, 10, 8)));
        when(licenseRepository.findAll(pageable)).thenReturn(page);

        // When
        Page<LicenseResponseDTO> resultado = licenseService.obtenerTodas(pageable);

        // Then
        assertThat(resultado.getTotalElements()).isEqualTo(1);
        verify(licenseRepository).findAll(pageable);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "totalCopias"));
    }

    @Test
    void obtenerTodas_conPaginaFueraDeRango_retornaVacio() {
        // Given: 3 licencias, página 10 de tamaño 5 — fuera de rango
        Page<License> page = new PageImpl<>(List.of(), PageRequest.of(10, 5), 3L);
        when(licenseRepository.findAll(any(Pageable.class))).thenReturn(page);

        // When
        Page<LicenseResponseDTO> resultado = licenseService.obtenerTodas(PageRequest.of(10, 5));

        // Then
        assertThat(resultado).isEmpty();
        assertThat(resultado.getNumberOfElements()).isZero();
        assertThat(resultado.getTotalElements()).isEqualTo(3);
        verify(licenseRepository).findAll(any(Pageable.class));
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
    void obtenerPorLibroId_noExiste_lanzaLicenciaNotFoundException() {
        when(licenseRepository.findByLibroId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> licenseService.obtenerPorLibroId(99L))
            .isInstanceOf(LicenciaNotFoundException.class)
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
    void crear_licenciaYaExiste_lanzaLicenciaDuplicadaException() {
        when(licenseRepository.findByLibroId(1L)).thenReturn(Optional.of(license(1L, 10, 8)));

        assertThatThrownBy(() -> licenseService.crear(request(1L, 5)))
            .isInstanceOf(LicenciaDuplicadaException.class)
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
            .isInstanceOf(CopiaNoDisponibleException.class)
            .hasMessageContaining("No hay copias disponibles del libro con id: 1");

        verify(licenseRepository, never()).save(any());
    }

    @Test
    void prestar_licenciaNoExiste_lanzaLicenciaNotFoundException() {
        when(licenseRepository.findByLibroId(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> licenseService.prestar(7L))
            .isInstanceOf(LicenciaNotFoundException.class)
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
    void devolver_todasDisponibles_lanzaDevolucionInvalidaException() {
        // copias disponibles == total copias → ninguna prestada, no hay que devolver
        License l = license(1L, 5, 5);
        when(licenseRepository.findByLibroId(1L)).thenReturn(Optional.of(l));

        assertThatThrownBy(() -> licenseService.devolver(1L))
            .isInstanceOf(DevolucionInvalidaException.class)
            .hasMessageContaining("Todas las copias del libro ya están disponibles");

        verify(licenseRepository, never()).save(any());
    }

    // =====================================================================
    // prestar() — optimistic locking: 3 reintentos ante conflicto @Version
    // =====================================================================

    @Test
    void prestar_con_3_conflictos_OL_consecutivos_lanza_ConflictosConcurrenciaException() {
        // Simula 3 conflictos de @Version seguidos — el wrapper agota reintentos
        License l = license(1L, 10, 3);
        when(licenseRepository.findByLibroId(1L)).thenReturn(Optional.of(l));
        when(licenseRepository.save(any(License.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException("license", null));

        assertThatThrownBy(() -> licenseService.prestar(1L))
            .isInstanceOf(ConflictosConcurrenciaException.class)
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
    void devolver_con_3_conflictos_OL_consecutivos_lanza_ErrorDevolucionException() {
        // Simula 3 conflictos de @Version seguidos en devolver()
        License l = license(1L, 10, 3);
        when(licenseRepository.findByLibroId(1L)).thenReturn(Optional.of(l));
        when(licenseRepository.save(any(License.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException("license", null));

        assertThatThrownBy(() -> licenseService.devolver(1L))
            .isInstanceOf(ErrorDevolucionException.class)
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
    void actualizar_reduciendoPorDebajoDePrestatarios_lanzaReduccionCopiasInvalidaException() {
        // total=5, disponibles=1 → 4 prestadas
        // No se puede reducir a 3 si hay 4 prestadas
        License l = license(1L, 5, 1);
        when(licenseRepository.findByLibroId(1L)).thenReturn(Optional.of(l));

        assertThatThrownBy(() -> licenseService.actualizar(1L, request(1L, 3)))
            .isInstanceOf(ReduccionCopiasInvalidaException.class)
            .hasMessageContaining("No se puede reducir a 3 copias");

        verify(licenseRepository, never()).save(any());
    }

    @Test
    void actualizar_licenciaNoExiste_lanzaLicenciaNotFoundException() {
        when(licenseRepository.findByLibroId(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> licenseService.actualizar(9L, request(9L, 5)))
            .isInstanceOf(LicenciaNotFoundException.class)
            .hasMessageContaining("No existe licencia para el libro con id: 9");
    }
}
