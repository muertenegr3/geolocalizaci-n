package com.example.geolocalizacion.controller;

import com.example.geolocalizacion.model.HistorialGeolocalizacion;
import com.example.geolocalizacion.model.UbicacionPaciente;
import com.example.geolocalizacion.service.UbicacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/geolocalizacion")
@CrossOrigin(origins = "*") 
public class UbicacionController {

    @Autowired
    private UbicacionService ubicacionService;

    @GetMapping("/health")
    public ResponseEntity<java.util.Map<String, Object>> salud() {
        java.util.Map<String, Object> estado = new java.util.HashMap<>();
        estado.put("status", "UP");
        estado.put("service", "geolocalizacion");
        return ResponseEntity.ok(estado);
    }

    @PostMapping("/actualizar")
    public ResponseEntity<UbicacionPaciente> actualizarUbicacion(@RequestBody UbicacionPaciente ubicacion) {
        UbicacionPaciente guardada = ubicacionService.registrarUbicacion(ubicacion);
        return ResponseEntity.ok(guardada);
    }

    @GetMapping("/{rut}")
    public ResponseEntity<UbicacionPaciente> obtenerUbicacionActual(@PathVariable String rut) {
        UbicacionPaciente ubicacion = ubicacionService.obtenerUltimaUbicacion(rut);
        if (ubicacion != null) {
            return ResponseEntity.ok(ubicacion);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{rut}/historial")
    public ResponseEntity<List<HistorialGeolocalizacion>> obtenerHistorial(
            @PathVariable String rut,
            @RequestParam(required = false) Integer limite) {
        List<HistorialGeolocalizacion> historial = ubicacionService.obtenerHistorial(rut, limite);
        if (historial.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(historial);
    }
}