package com.example.geolocalizacion.controller;

import com.example.geolocalizacion.model.HistorialGeolocalizacion;
import com.example.geolocalizacion.model.UbicacionPaciente;
import com.example.geolocalizacion.service.IdentificadorInvalidoException;
import com.example.geolocalizacion.service.UbicacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    /**
     * Recibe el RUN cifrado que el APK tiene en su sesion, lo descifra y
     * persiste solo el SHA-256. Si no se puede resolver, responde 400 en vez
     * de 500, para que un fallo de APP_CRYPTO_* sea distinguible de una caida.
     */
    @PostMapping("/actualizar")
    public ResponseEntity<?> actualizarUbicacion(@RequestBody UbicacionPaciente ubicacion) {
        try {
            UbicacionPaciente guardada = ubicacionService.registrarUbicacion(ubicacion);
            return ResponseEntity.ok(guardada);
        } catch (IdentificadorInvalidoException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
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