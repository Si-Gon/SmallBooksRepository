package com.silvio.license.service;

import com.silvio.license.dto.LicenseRequestDTO;
import com.silvio.license.dto.LicenseResponseDTO;
import com.silvio.license.exception.CopiaNoDisponibleException;
import com.silvio.license.exception.ConflictosConcurrenciaException;
import com.silvio.license.exception.DevolucionInvalidaException;
import com.silvio.license.exception.ErrorDevolucionException;
import com.silvio.license.exception.LicenciaDuplicadaException;
import com.silvio.license.exception.ReduccionCopiasInvalidaException;
import com.silvio.license.model.License;
import com.silvio.license.repository.LicenseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.observation.annotation.Observed;
import java.util.NoSuchElementException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Slf4j
@Service
@RequiredArgsConstructor
public class LicenseService {

    private final LicenseRepository licenseRepository;

    @Observed(name = "license.obtenerTodas")
    @Transactional(readOnly = true)
    public Page<LicenseResponseDTO> obtenerTodas(Pageable pageable) {
        if (pageable.isUnpaged()) {
            log.info("Consultando todas las licencias — sin paginación");
        } else {
            log.info("Consultando todas las licencias — página: {}, tamaño: {}",
                    pageable.getPageNumber(), pageable.getPageSize());
        }
        return licenseRepository.findAll(pageable)
                .map(this::mapearADto);
    }

    @Observed(name = "license.obtenerPorLibroId")
    @Transactional(readOnly = true)
    public LicenseResponseDTO obtenerPorLibroId(Long libroId) {
        log.info("Consultando licencia para libro id: {}", libroId);
        License license = licenseRepository.findByLibroId(libroId)
                .orElseThrow(() -> {
                    log.warn("Licencia no encontrada para libro id: {}", libroId);
                    return new NoSuchElementException("No existe licencia para el libro con id: " + libroId);
                });
        return mapearADto(license);
    }

    @Observed(name = "license.crear")
    public LicenseResponseDTO crear(LicenseRequestDTO request) {
        log.info("Creando licencia para libro id: {}, total copias: {}",
                request.getLibroId(), request.getTotalCopias());

        licenseRepository.findByLibroId(request.getLibroId())
                .ifPresent(l -> {
                    log.warn("Ya existe licencia para libro id: {}", request.getLibroId());
                    throw new LicenciaDuplicadaException(request.getLibroId());
                });

        License license = new License();
        license.setLibroId(request.getLibroId());
        license.setTotalCopias(request.getTotalCopias());
        license.setCopiasDisponibles(request.getTotalCopias());

        License guardada = licenseRepository.save(license);
        log.info("Licencia creada — id: {}, libro: {}, copias: {}",
                guardada.getId(), guardada.getLibroId(), guardada.getTotalCopias());
        return mapearADto(guardada);
    }

    // ─── prestar con optimistic locking ─────────────────────────────────────
    // Wrapper no transaccional: reintenta si hay conflicto de concurrencia
    // El @Version en License dispara ObjectOptimisticLockingFailureException
    // cuando dos hilos modifican la misma fila simultáneamente

    @Observed(name = "license.prestarWrapper")
    public LicenseResponseDTO prestar(Long libroId) {
        int maxReintentos = 3;
        int intento = 0;
        while (true) {
            try {
                return doPrestar(libroId);
            } catch (ObjectOptimisticLockingFailureException e) {
                if (++intento >= maxReintentos) {
                    log.error("No se pudo descontar copia tras {} intentos — libro: {}",
                            maxReintentos, libroId);
                    throw new ConflictosConcurrenciaException();
                }
                log.warn("Conflicto de concurrencia al prestar libro {}, reintento {}/{}",
                        libroId, intento, maxReintentos);
            }
        }
    }

    @Observed(name = "license.prestar")
    @Transactional
    protected LicenseResponseDTO doPrestar(Long libroId) {
        log.info("Descontando copia — libro id: {}", libroId);
        License license = licenseRepository.findByLibroId(libroId)
                .orElseThrow(() -> new NoSuchElementException("No existe licencia para el libro con id: " + libroId));

        if (license.getCopiasDisponibles() <= 0) {
            log.warn("Sin copias disponibles — libro id: {}", libroId);
            throw new CopiaNoDisponibleException(libroId);
        }

        license.setCopiasDisponibles(license.getCopiasDisponibles() - 1);
        log.info("Copia descontada — libro: {}, disponibles: {}/{}",
                libroId, license.getCopiasDisponibles(), license.getTotalCopias());
        return mapearADto(licenseRepository.save(license));
    }

    // ─── devolver con optimistic locking ────────────────────────────────────

    @Observed(name = "license.devolverWrapper")
    public LicenseResponseDTO devolver(Long libroId) {
        int maxReintentos = 3;
        int intento = 0;
        while (true) {
            try {
                return doDevolver(libroId);
            } catch (ObjectOptimisticLockingFailureException e) {
                if (++intento >= maxReintentos) {
                    log.error("No se pudo devolver copia tras {} intentos — libro: {}",
                            maxReintentos, libroId);
                    throw new ErrorDevolucionException();
                }
                log.warn("Conflicto de concurrencia al devolver libro {}, reintento {}/{}",
                        libroId, intento, maxReintentos);
            }
        }
    }

    @Observed(name = "license.devolver")
    @Transactional
    protected LicenseResponseDTO doDevolver(Long libroId) {
        log.info("Devolviendo copia — libro id: {}", libroId);
        License license = licenseRepository.findByLibroId(libroId)
                .orElseThrow(() -> new NoSuchElementException("No existe licencia para el libro con id: " + libroId));

        if (license.getCopiasDisponibles() >= license.getTotalCopias()) {
            log.warn("Intento de devolver copia cuando todas están disponibles — libro: {}", libroId);
            throw new DevolucionInvalidaException();
        }

        license.setCopiasDisponibles(license.getCopiasDisponibles() + 1);
        log.info("Copia devuelta — libro: {}, disponibles: {}/{}",
                libroId, license.getCopiasDisponibles(), license.getTotalCopias());
        return mapearADto(licenseRepository.save(license));
    }

    @Observed(name = "license.actualizar")
    @Transactional
    public LicenseResponseDTO actualizar(Long libroId, LicenseRequestDTO request) {
        log.info("Actualizando licencia libro id: {}, nuevo total: {}",
                libroId, request.getTotalCopias());
        License license = licenseRepository.findByLibroId(libroId)
                .orElseThrow(() -> new NoSuchElementException("No existe licencia para el libro con id: " + libroId));

        int copiasPrestadas = license.getTotalCopias() - license.getCopiasDisponibles();
        if (request.getTotalCopias() < copiasPrestadas) {
            log.warn("No se puede reducir a {} copias — hay {} prestadas", 
                    request.getTotalCopias(), copiasPrestadas);
            throw new ReduccionCopiasInvalidaException(request.getTotalCopias(), copiasPrestadas);
        }

        license.setTotalCopias(request.getTotalCopias());
        license.setCopiasDisponibles(request.getTotalCopias() - copiasPrestadas);
        log.info("Licencia actualizada — libro: {}, total: {}, disponibles: {}",
                libroId, license.getTotalCopias(), license.getCopiasDisponibles());
        return mapearADto(licenseRepository.save(license));
    }

    private LicenseResponseDTO mapearADto(License license) {
        LicenseResponseDTO dto = new LicenseResponseDTO();
        dto.setId(license.getId());
        dto.setLibroId(license.getLibroId());
        dto.setTotalCopias(license.getTotalCopias());
        dto.setCopiasDisponibles(license.getCopiasDisponibles());
        return dto;
    }
}