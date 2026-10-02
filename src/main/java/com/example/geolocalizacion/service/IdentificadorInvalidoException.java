package com.example.geolocalizacion.service;

/**
 * El identificador recibido no se pudo resolver al SHA-256 del RUN.
 *
 * Causas habituales: el ciphertext no es valido, o APP_CRYPTO_PASSWORD /
 * APP_CRYPTO_SALT de este servicio no coinciden con los de
 * hydra_arm_security, que es quien cifro el valor.
 */
public class IdentificadorInvalidoException extends RuntimeException {

    public IdentificadorInvalidoException(String mensaje) {
        super(mensaje);
    }
}