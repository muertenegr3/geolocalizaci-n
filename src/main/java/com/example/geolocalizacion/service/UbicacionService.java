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

    /** SHA-256 en hexadecimal: exactamente 64 caracteres en [0-9a-f]. */
    private static final String PATRON_HASH = "^[0-9a-f]{64}$";

    @Autowired
    private UbicacionRepository repository;

    @Autowired
    private HistorialGeolocalizacionRepository historialRepository;

    @Autowired
    private EncryptionService encryptionService;

    /**
     * Traduce el identificador que llega del cliente al valor realmente
     * almacenado en la columna.
     *
     * El APK no tiene el RUN en texto plano: guarda en la sesion el ciphertext
     * que devolvio el backend (login -> dbId) y ese mismo valor viaja tanto en
     * el POST como en los GET. Por eso TODA lectura y escritura pasa por aqui:
     * se descifra y se hashea, de modo que en la base solo exista el SHA-256.
     *
     * Acepta tambien un hash ya calculado, para no fallar si algun cliente
     * cambia a enviar el hash directamente.
     *
     * @return el SHA-256 del RUN, o null si el valor no se puede resolver
     */
    private String normalizar(String identificador) {
        if (identificador == null || identificador.isBlank()) {
            return null;
        }
        String trimmed = identificador.trim();
        if (trimmed.matches(PATRON_HASH)) {
            return trimmed;
        }
        String runPlano = encryptionService.desencriptar(trimmed);
        if (runPlano == null || runPlano.isBlank()) {
            return null;
        }
        return HashUtils.HASHEO(runPlano);
    }

    /**
     * Registra la ubicacion: una fila actual por paciente (upsert) mas una
     * fila de historial. Ambas comparten el identificador ya hasheado.
     */
    @Transactional
    public UbicacionPaciente registrarUbicacion(UbicacionPaciente ubicacion) {
        String hash = normalizar(ubicacion.getRutPaciente());
        if (hash == null) {
            throw new IdentificadorInvalidoException(
                    "No se pudo resolver el RUN: ciphertext invalido o credenciales APP_CRYPTO_* incorrectas");
        }

        LocalDateTime ahora = LocalDateTime.now();
        ubicacion.setRutPaciente(hash);

        Optional<UbicacionPaciente> existente =
                repository.findFirstByRutPacienteOrderByFechaReporteDesc(hash);
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
        historial.setRunPaciente(hash);
        historial.setFecha(ahora);
        historial.setHora(LocalTime.now());
        historial.setLatitud(ubicacion.getLatitud());
        historial.setLongitud(ubicacion.getLongitud());
        historialRepository.save(historial);

        return ubicacion;
    }

    public UbicacionPaciente obtenerUltimaUbicacion(String identificador) {
        String hash = normalizar(identificador);
        if (hash == null) {
            return null;
        }
        return repository.findFirstByRutPacienteOrderByFechaReporteDesc(hash).orElse(null);
    }

    public List<HistorialGeolocalizacion> obtenerHistorial(String identificador, Integer limite) {
        String hash = normalizar(identificador);
        if (hash == null) {
            return List.of();
        }
        int cantidad = (limite == null || limite < 1) ? 20 : Math.min(limite, 200);
        return historialRepository.findByRunPacienteOrderByFechaDesc(hash, PageRequest.of(0, cantidad));
    }
}