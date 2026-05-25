package com.silvio.license.service;

import com.silvio.license.dto.LicenseRequestDTO;
import com.silvio.license.dto.LicenseResponseDTO;
import com.silvio.license.model.License;
import com.silvio.license.repository.LicenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LicenseService {

    private final LicenseRepository licenseRepository;

    // GET todas las licencias
   
    public List<LicenseResponseDTO> obtenerTodas() {
        return licenseRepository.findAll()
                .stream()
                .map(this::mapearADto)
                .collect(Collectors.toList());
    }

    // GET por libroId — usado por E-Lending via Feign
    
    public LicenseResponseDTO obtenerPorLibroId(Long libroId) {
        License licencia = licenseRepository.findByLibroId(libroId)
                .orElseThrow(() -> new RuntimeException(
                        "No existe licencia para el libro con id: " + libroId));
        return mapearADto(licencia);
    }

    // POST — registrar licencia para un libro
    // Al crear, copiasDisponibles = totalCopias (todas disponibles)
    public LicenseResponseDTO crear(LicenseRequestDTO request) {

        // Verificar que no exista ya una licencia para ese libro
        licenseRepository.findByLibroId(request.getLibroId())
                .ifPresent(l -> {
                    throw new RuntimeException(
                            "Ya existe una licencia para el libro con id: " + request.getLibroId());
                });

        License licencia = new License();
        licencia.setLibroId(request.getLibroId());
        licencia.setTotalCopias(request.getTotalCopias());
        licencia.setCopiasDisponibles(request.getTotalCopias()); // todas disponibles al inicio

        return mapearADto(licenseRepository.save(licencia));
    }

    // PATCH — descontar 1 copia cuando E-Lending crea un préstamo
    // @Transactional garantiza que si algo falla, no se descuenta la copia

    @Transactional
    public LicenseResponseDTO prestar(Long libroId) {
        License licencia = licenseRepository.findByLibroId(libroId)
                .orElseThrow(() -> new RuntimeException(
                        "No existe licencia para el libro con id: " + libroId));

        if (licencia.getCopiasDisponibles() <= 0) {
            throw new RuntimeException(
                    "No hay copias disponibles del libro con id: " + libroId);
        }

        licencia.setCopiasDisponibles(licencia.getCopiasDisponibles() - 1);
        return mapearADto(licenseRepository.save(licencia));
    }

    // PATCH — devolver 1 copia cuando E-Lending cierra un préstamo
    
    @Transactional
    public LicenseResponseDTO devolver(Long libroId) {
        License licencia = licenseRepository.findByLibroId(libroId)
                .orElseThrow(() -> new RuntimeException(
                        "No existe licencia para el libro con id: " + libroId));

        // No puede haber más copias disponibles que el total
        if (licencia.getCopiasDisponibles() >= licencia.getTotalCopias()) {
            throw new RuntimeException(
                    "Todas las copias del libro ya están disponibles");
        }

        licencia.setCopiasDisponibles(licencia.getCopiasDisponibles() + 1);
        return mapearADto(licenseRepository.save(licencia));
    }

    // PUT — actualizar total de copias (admin puede aumentar o reducir)
    
    @Transactional
    public LicenseResponseDTO actualizar(Long libroId, LicenseRequestDTO request) {
        License licencia = licenseRepository.findByLibroId(libroId)
                .orElseThrow(() -> new RuntimeException(
                        "No existe licencia para el libro con id: " + libroId));

        // Calcular cuántas copias están prestadas actualmente
        int copiasPrestadas = licencia.getTotalCopias() - licencia.getCopiasDisponibles();

        // El nuevo total no puede ser menor a las copias ya prestadas
        if (request.getTotalCopias() < copiasPrestadas) {
            throw new RuntimeException(
                    "No se puede reducir a " + request.getTotalCopias() +
                    " copias — hay " + copiasPrestadas + " copias actualmente prestadas");
        }

        licencia.setTotalCopias(request.getTotalCopias());
        licencia.setCopiasDisponibles(request.getTotalCopias() - copiasPrestadas);

        return mapearADto(licenseRepository.save(licencia));
    }

    // Mapeo privado Entidad → ResponseDTO
    
    private LicenseResponseDTO mapearADto(License licencia) {
        LicenseResponseDTO dto = new LicenseResponseDTO();
        dto.setId(licencia.getId());
        dto.setLibroId(licencia.getLibroId());
        dto.setTotalCopias(licencia.getTotalCopias());
        dto.setCopiasDisponibles(licencia.getCopiasDisponibles());
        return dto;
    }
}