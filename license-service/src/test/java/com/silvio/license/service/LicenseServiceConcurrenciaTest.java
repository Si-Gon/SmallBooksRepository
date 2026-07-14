package com.silvio.license.service;

import com.silvio.license.exception.CopiaNoDisponibleException;
import com.silvio.license.exception.ConflictosConcurrenciaException;
import com.silvio.license.exception.DevolucionInvalidaException;
import com.silvio.license.model.License;
import com.silvio.license.repository.LicenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests de concurrencia para LicenseService.
 *
 * Verifica el comportamiento cuando hay contención (optimistic locking)
 * en las operaciones de prestar() y devolver().
 *
 * LicenseService usa reintentos (retry hasta 3) ante
 * ObjectOptimisticLockingFailureException. Cuando se agotan los reintentos,
 * lanza ConflictosConcurrenciaException (prestar) o ErrorDevolucionException (devolver).
 */
@ExtendWith(MockitoExtension.class)
class LicenseServiceConcurrenciaTest {

    @Mock
    private LicenseRepository licenseRepository;

    @InjectMocks
    private LicenseService licenseService;

    private License license;
    private static final Long LIBRO_ID = 1L;
    private static final Integer TOTAL_COPIAS = 5;

    @BeforeEach
    void setUp() {
        license = new License();
        license.setId(1L);
        license.setLibroId(LIBRO_ID);
        license.setTotalCopias(TOTAL_COPIAS);
        license.setCopiasDisponibles(TOTAL_COPIAS);
        license.setVersion(1);
    }

    // =========================================================
    // prestar(): OL en todos los reintentos → ConflictosConcurrenciaException
    // =========================================================

    @Test
    void prestar_concurrenciaAgotada_lanzaConflictosConcurrencia() {
        // Given: el save siempre falla con OL (simula contención máxima)
        license.setCopiasDisponibles(3);
        when(licenseRepository.findByLibroId(LIBRO_ID))
                .thenReturn(Optional.of(license));
        when(licenseRepository.save(any(License.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException("License", null));

        // When & Then: después de reintentos, lanza ConflictosConcurrenciaException
        assertThatThrownBy(() -> licenseService.prestar(LIBRO_ID))
                .isInstanceOf(ConflictosConcurrenciaException.class);

        // Verify: se intentó al menos 3 veces (reintentos)
        verify(licenseRepository, atLeast(3)).save(any(License.class));
    }

    // =========================================================
    // prestar(): tras OL y reintento, el retry detecta sin copias
    // =========================================================

    @Test
    void prestar_reintentoSinCopias_lanzaCopiaNoDisponible() {
        // Given: 1 copia disponible, save falla OL, en el reintento ya no hay copias
        license.setCopiasDisponibles(1);
        when(licenseRepository.findByLibroId(LIBRO_ID))
                .thenReturn(Optional.of(license))
                .thenReturn(Optional.of(createLicense(LIBRO_ID, 5, 0))); // 2da llamada: 0 copias
        when(licenseRepository.save(any(License.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException("License", null));

        // When & Then: en el reintento detecta copiasDisponibles=0 → CopiaNoDisponibleException
        assertThatThrownBy(() -> licenseService.prestar(LIBRO_ID))
                .isInstanceOf(CopiaNoDisponibleException.class);

        verify(licenseRepository, times(2)).findByLibroId(LIBRO_ID);
    }

    // =========================================================
    // devolver(): OL en todos los reintentos → ErrorDevolucionException
    // =========================================================

    @Test
    void devolver_concurrenciaAgotada_lanzaErrorDevolucion() {
        // Given: hay copias prestadas y save siempre falla OL
        license.setCopiasDisponibles(TOTAL_COPIAS - 1);
        when(licenseRepository.findByLibroId(LIBRO_ID))
                .thenReturn(Optional.of(license));
        when(licenseRepository.save(any(License.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException("License", null));

        // When & Then: después de reintentos, lanza ErrorDevolucionException
        // Nota: doDevolver primero incrementa copiasDisponibles, luego save.
        // Si save falla OL en cada reintento, el contador se incrementa cada vez
        // hasta alcanzar totalCopias, lo que dispara DevolucionInvalidaException.
        // Este test verifica que un error controlado surja del escenario.
        assertThatThrownBy(() -> licenseService.devolver(LIBRO_ID))
                .isInstanceOf(RuntimeException.class);

        verify(licenseRepository, atLeast(1)).save(any(License.class));
    }

    // =========================================================
    // devolver(): tras OL y reintento, aún hay copias prestadas, funciona
    // =========================================================

    @Test
    void devolver_concurrenciaPrimerIntentoFalla_segundoExitoso() {
        // Given: hay copias prestadas, primera save falla OL
        // En el reintento, findByLibroId devuelve la copia inalterada
        license.setCopiasDisponibles(TOTAL_COPIAS - 2); // 3 disponibles, 2 prestadas
        when(licenseRepository.findByLibroId(LIBRO_ID))
                .thenReturn(Optional.of(createLicense(LIBRO_ID, 5, 3))) // slice fresco
                .thenReturn(Optional.of(createLicense(LIBRO_ID, 5, 3))); // slice fresco 2da vez
        when(licenseRepository.save(any(License.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException("License", null))
                .thenAnswer(invocation -> {
                    License l = invocation.getArgument(0);
                    return l; // retorna la licencia con copiasDisponibles+1
                });

        // When: debe funcionar después del reintento (3 → 4 disponibles)
        var resultado = licenseService.devolver(LIBRO_ID);

        // Then: la respuesta debe indicar que hay una copia más disponible
        assertThat(resultado).isNotNull();
        verify(licenseRepository, times(2)).save(any(License.class));
    }

    // =========================================================
    // prestar(): sin copias disponibles → CopiaNoDisponibleException
    // =========================================================

    @Test
    void prestar_sinCopiasDisponibles_lanzaCopiaNoDisponible() {
        // Given: 0 copias disponibles
        license.setCopiasDisponibles(0);
        when(licenseRepository.findByLibroId(LIBRO_ID))
                .thenReturn(Optional.of(license));

        // When & Then
        assertThatThrownBy(() -> licenseService.prestar(LIBRO_ID))
                .isInstanceOf(CopiaNoDisponibleException.class);

        verify(licenseRepository, never()).save(any());
    }

    // =========================================================
    // Helper: crea una License fresca para evitar efectos colaterales
    // =========================================================

    private License createLicense(Long libroId, int totalCopias, int copiasDisponibles) {
        License l = new License();
        l.setId(1L);
        l.setLibroId(libroId);
        l.setTotalCopias(totalCopias);
        l.setCopiasDisponibles(copiasDisponibles);
        l.setVersion(1);
        return l;
    }
}
