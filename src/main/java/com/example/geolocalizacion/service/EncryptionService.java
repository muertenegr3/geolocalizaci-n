package com.example.geolocalizacion.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Descifrado del RUN que el APK envia cifrado.
 *
 * El APK NO tiene el RUN en texto plano: login guarda en la sesion el
 * ciphertext que devuelve el backend (usuario-service.ts -> dbId), y ese valor
 * es el que viaja en POST /api/geolocalizacion/actualizar como rutPaciente.
 *
 * El ciphertext lo produjo hydra_arm_security con Encryptors.text, asi que
 * este servicio DEBE compartir APP_CRYPTO_PASSWORD y APP_CRYPTO_SALT con
 * aquel. Si las claves no coinciden, Desencriptar devuelve null y el
 * UbicacionService rechaza el POST con 400 en vez de guardar basura.
 *
 * Se descifra solo para poder hashear: lo que se persiste es el SHA-256, nunca
 * el RUN en claro.
 *
 * IMPORTANTE sobre el salt: Encryptors.text lo interpreta como HEX, no como
 * texto. Si APP_CRYPTO_SALT no es una cadena hexadecimal de longitud par, el
 * servicio lanza IllegalArgumentException al arrancar ("Hex-encoded string must
 * have an even number of characters"). El valor debe ser el mismo que usa
 * hydra_arm_security.
 */
@Service
public class EncryptionService {

    @Value("${app.crypto.password}")
    private String password;

    @Value("${app.crypto.salt}")
    private String salt;

    private TextEncryptor encryptor;

    @PostConstruct
    public void init() {
        this.encryptor = Encryptors.text(password, salt);
    }

    /**
     * Recupera el RUN original desde el ciphertext.
     *
     * hydra_arm_security.encriptarRobusto() concatena "|" + timestamp antes de
     * cifrar, asi que hay que cortar ese sufijo.
     *
     * @return el RUN en texto plano, o null si el ciphertext no es valido
     */
    public String desencriptar(String datosCifrados) {
        if (datosCifrados == null || datosCifrados.isBlank()) {
            return null;
        }
        try {
            String desencriptado = encryptor.decrypt(datosCifrados);
            int separador = desencriptado.indexOf('|');
            return separador > 0 ? desencriptado.substring(0, separador) : desencriptado;
        } catch (Exception e) {
            return null;
        }
    }
}