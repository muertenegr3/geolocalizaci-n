package com.example.geolocalizacion.service;

import com.example.geolocalizacion.model.HistorialGeolocalizacion;
import com.example.geolocalizacion.model.UbicacionPaciente;
import com.example.geolocalizacion.repository.HistorialGeolocalizacionRepository;
import com.example.geolocalizacion.repository.UbicacionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class UbicacionService {

    @Autowired
    private UbicacionRepository repository;

    @Autowired
    private HistorialGeolocalizacionRepository historialRepository;

    @Transactional
    public UbicacionPaciente registrarUbicacion(UbicacionPaciente ubicacion) {
        LocalDateTime ahora = LocalDateTime.now();

        Optional<UbicacionPaciente> existente = repository.findFirstByRutPacienteOrderByFechaReporteDesc(ubicacion.getRutPaciente());
        if (existente.isPresent()) {
            UbicacionPaciente actual = existente.get();
            actual.setLatitud(ubicacion.getLatitud());
            actual.setLongitud(ubicacion.getLongitud());
            actual.setFechaReporte(ahora);
            ubicacion = repository.save(actual);
        } else {
            ubicacion.setFechaReporte(ahora);
            ubicacion = repository.save(ubicacion);
        }

        HistorialGeolocalizacion historial = new HistorialGeolocalizacion();
        historial.setRunPaciente(ubicacion.getRutPaciente());
        historial.setFecha(ahora);
        historial.setHora(LocalTime.now());
        historial.setLatitud(ubicacion.getLatitud());
        historial.setLongitud(ubicacion.getLongitud());
        historialRepository.save(historial);

        return ubicacion;
    }

    public UbicacionPaciente obtenerUltimaUbicacion(String rut) {
        Optional<UbicacionPaciente> ubicacion = repository.findFirstByRutPacienteOrderByFechaReporteDesc(rut);
        return ubicacion.orElse(null);
    }

    public List<HistorialGeolocalizacion> obtenerHistorial(String rut, Integer limite) {
        int cantidad = (limite == null || limite < 1) ? 20 : Math.min(limite, 200);
        return historialRepository.findByRunPacienteOrderByFechaDesc(rut, PageRequest.of(0, cantidad));
    }
}