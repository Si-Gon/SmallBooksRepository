package com.silvio.elending.repository;

import com.silvio.elending.model.Prestamo;
import com.silvio.elending.model.Prestamo.EstadoPrestamo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PrestamoRepository extends JpaRepository<Prestamo, Long> {

    // Préstamos activos de un usuario — para verificar límite de 5
    List<Prestamo> findByUsuarioIdAndEstado(String usuarioId, EstadoPrestamo estado);

    // Préstamos activos de un libro — para verificar si el usuario ya tiene ese libro
    List<Prestamo> findByLibroIdAndEstado(Long libroId, EstadoPrestamo estado);

    // Préstamos vencidos que el scheduler debe cerrar
    // SELECT * FROM prestamos WHERE estado = 'ACTIVO' AND fecha_vencimiento < ahora
    List<Prestamo> findByEstadoAndFechaVencimientoBefore(
            EstadoPrestamo estado, LocalDateTime ahora);

    // Préstamos próximos a vencer entre desde y hasta — evita traer vencidos y filtrar en memoria
    // SELECT * FROM prestamos WHERE estado = 'ACTIVO' AND fecha_vencimiento BETWEEN desde AND hasta
    List<Prestamo> findByEstadoAndFechaVencimientoBetween(
            EstadoPrestamo estado, LocalDateTime desde, LocalDateTime hasta);

    // Historial completo de un usuario — para Analytics
    List<Prestamo> findByUsuarioId(String usuarioId);
}