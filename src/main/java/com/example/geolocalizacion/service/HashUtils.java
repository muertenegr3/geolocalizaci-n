package com.example.geolocalizacion.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.stereotype.Component;

/**
 * SHA-256 determinista, idéntico a hydra_Crud.app.Utils.HashUtils y a
 * hydra_arm_security.app.Utils.HashUtils.
 *
 * Debe mantenerse en sincronía con aquellos: los clientes calculan el hash con
 * GET /api/user/cripto/hash y comparan contra lo que se guarda en la columna.
 * Si la normalización cambia en un solo lado, el filtrado deja de funcionar en
 * silencio.
 */
@Component
public class HashUtils {

    public static String HASHEO(String run) {
        if (run == null || run.isBlank()) {
            throw new IllegalArgumentException("El RUN a hashear no puede ser null ni vacio");
        }
        try {
            String runLimpio = run.replace(".", "").replace(" ", "").toLowerCase();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(runLimpio.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder(2 * encodedHash.length);
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error crítico: no se encontró el algoritmo SHA-256", e);
        }
    }
}