package com.example.geolocalizacion.repository;

import com.example.geolocalizacion.model.UbicacionPaciente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UbicacionRepository extends JpaRepository<UbicacionPaciente, Long> {
    Optional<UbicacionPaciente> findFirstByRutPacienteOrderByFechaReporteDesc(String rutPaciente);
}