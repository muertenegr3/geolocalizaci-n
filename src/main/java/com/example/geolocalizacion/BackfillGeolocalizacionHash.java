package com.example.geolocalizacion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * Backfill idempotente: reemplaza el ciphertext AES-GCM de las columnas de
 * identificador por el SHA-256 del RUN.
 *
 * Por que no se hace en SQL: las columnas guardan ciphertext y pgcrypto solo
 * sabe cifrar, no descifrar. Hace falta la clave que uso hydra_arm_security.
 *
 * Por que no se hace con un endpoint: aqui corre contra la base una sola vez y
 * no deja un servicio publico que exponga RUNs.
 *
 * IMPORTANTE: el descifrado usa Encryptors.text, la misma clase que aplico
 * hydra_arm_security al cifrar. No se reimplementa AES-GCM a mano, porque los
 * parametros de derivacion de clave (iteraciones PBKDF2, formato del IV) son
 * los de Spring y hardcodearlos es la forma facil de obtener un null silencioso.
 *
 * Uso (desde la raiz de geolocalizaci-n):
 *   set SPRING_DATASOURCE_URL=...
 *   set SPRING_DATASOURCE_USERNAME=...
 *   set SPRING_DATASOURCE_PASSWORD=...
 *   set APP_CRYPTO_PASSWORD=...
 *   set APP_CRYPTO_SALT=...
 *   mvnw -o compile
 *   mvnw -o exec:java -Dexec.mainClass=com.example.geolocalizacion.BackfillGeolocalizacionHash
 *
 * Con DRY_RUN=true solo informa y no escribe. Conviene empezar asi.
 */
public final class BackfillGeolocalizacionHash {

    private static final String PATRON_HASH = "^[0-9a-f]{64}$";

    public static void main(String[] args) throws Exception {
        boolean dryRun = Boolean.parseBoolean(env("DRY_RUN", "false"));

        String url = require("SPRING_DATASOURCE_URL");
        String user = require("SPRING_DATASOURCE_USERNAME");
        String password = require("SPRING_DATASOURCE_PASSWORD");

        TextEncryptor encryptor = Encryptors.text(
                require("APP_CRYPTO_PASSWORD"),
                require("APP_CRYPTO_SALT"));

        System.out.println("Modo: " + (dryRun ? "DRY RUN (no escribe)" : "ESCRITURA"));
        System.out.println("Base: " + sanitizar(url));

        try (Connection cx = DriverManager.getConnection(url, user, password)) {

            // Una fila por identificador: evita descifrar N veces lo mismo.
            Map<String, String> resoluciones = new LinkedHashMap<>();
            try (Statement st = cx.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT DISTINCT v FROM ("
                                 + " SELECT paciente_run_p AS v FROM public.geolocalizacion"
                                 + "  WHERE paciente_run_p IS NOT NULL"
                                 + "  UNION ALL"
                                 + " SELECT run_p FROM public.historial_geolocalizacion"
                                 + "  WHERE run_p IS NOT NULL"
                                 + ") s WHERE v <> ''")) {

                while (rs.next()) {
                    String valor = rs.getString(1);
                    if (valor == null || valor.isBlank()) {
                        continue;
                    }
                    if (valor.matches(PATRON_HASH)) {
                        resoluciones.put(valor, valor);
                        continue;
                    }
                    String run = desencriptar(encryptor, valor);
                    if (run == null) {
                        System.out.println("  [AVISO] no se pudo descifrar, se deja intacto: "
                                + recortar(valor));
                        resoluciones.put(valor, null);
                    } else {
                        resoluciones.put(valor, HASHEO(run));
                    }
                }
            }

            System.out.println("Identificadores distintos: " + resoluciones.size());
            for (Map.Entry<String, String> e : resoluciones.entrySet()) {
                System.out.println("  " + recortar(e.getKey()) + " -> "
                        + (e.getValue() == null ? "[sin resolver]" : e.getValue()));
            }

            List<Map.Entry<String, String>> aplicables = new ArrayList<>();
            for (Map.Entry<String, String> e : resoluciones.entrySet()) {
                if (e.getValue() != null && !e.getValue().equals(e.getKey())) {
                    aplicables.add(e);
                }
            }

            if (dryRun) {
                System.out.println("DRY RUN: no se escribio nada. "
                        + aplicables.size() + " identificadores a convertir.");
                return;
            }
            if (aplicables.isEmpty()) {
                System.out.println("Nada que convertir: todos los valores ya son hashes.");
                return;
            }

            // El UPDATE va por valor y no por id: un mismo identificador puede
            // estar repetido en miles de filas del historial.
            cx.setAutoCommit(false);
            try {
                int total = actualizar(cx,
                        "UPDATE public.geolocalizacion SET paciente_run_p = ? "
                                + " WHERE paciente_run_p = ? AND paciente_run_p !~ '^[0-9a-f]{64}$'",
                        "geolocalizacion.paciente_run_p", aplicables);
                total += actualizar(cx,
                        "UPDATE public.historial_geolocalizacion SET run_p = ? "
                                + " WHERE run_p = ? AND run_p !~ '^[0-9a-f]{64}$'",
                        "historial_geolocalizacion.run_p", aplicables);
                cx.commit();
                System.out.println("Filas actualizadas: " + total);
            } catch (Exception e) {
                cx.rollback();
                throw e;
            } finally {
                cx.setAutoCommit(true);
            }
        }
    }

    /** Igual que el metodo estatico de HashUtils: quita . y espacios, y pasa a minusculas. */
    static String HASHEO(String run) throws Exception {
        String runLimpio = run.replace(".", "").replace(" ", "").toLowerCase();
        byte[] h = MessageDigest.getInstance("SHA-256")
                .digest(runLimpio.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(2 * h.length);
        for (byte b : h) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    /**
     * hydra_arm_security.encriptarRobusto() concatena "|" + timestamp antes de
     * cifrar, asi que hay que cortar ese sufijo.
     *
     * @return el RUN en texto plano, o null si el ciphertext no cuadra
     */
    private static String desencriptar(TextEncryptor encryptor, String base64) {
        try {
            String desencriptado = encryptor.decrypt(base64);
            int separador = desencriptado.indexOf('|');
            return separador > 0 ? desencriptado.substring(0, separador) : desencriptado;
        } catch (Exception e) {
            return null;
        }
    }

    private static int actualizar(Connection cx, String sql, String nombreColumna,
                                  List<Map.Entry<String, String>> pares) throws Exception {
        int total = 0;
        try (PreparedStatement ps = cx.prepareStatement(sql)) {
            for (Map.Entry<String, String> e : pares) {
                ps.setString(1, e.getValue());
                ps.setString(2, e.getKey());
                int n = ps.executeUpdate();
                total += n;
                if (n > 0) {
                    System.out.println("  " + nombreColumna + ": " + n + " filas -> " + e.getValue());
                }
            }
        }
        return total;
    }

    private static String env(String nombre, String porDefecto) {
        String v = System.getenv(nombre);
        return (v == null || v.isBlank()) ? porDefecto : v;
    }

    private static String require(String nombre) {
        String v = System.getenv(nombre);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Falta la variable de entorno " + nombre);
        }
        return v;
    }

    private static String recortar(String s) {
        return s.length() <= 28 ? s : s.substring(0, 28) + "...";
    }

    /** No imprimir la URL completa: suele traer la clave de la BD en la query string. */
    private static String sanitizar(String url) {
        return url.replaceAll("(?i)(password|key|token)=[^&]*", "$1=***");
    }

    private BackfillGeolocalizacionHash() {}
}