package com.codeconheiner.tienda.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

/** Utilidades criptográficas compartidas (firmas de webhooks de pasarelas). */
public final class Hashes {

    private Hashes() {
    }

    /** SHA-256 en hexadecimal minúsculo del texto dado. */
    public static String sha256Hex(String texto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    /** Comparación en tiempo constante para firmas. */
    public static boolean igual(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /** Codificación Basic para autenticación con servicios externos. */
    public static String basic(String usuario, String clave) {
        return Base64.getEncoder().encodeToString(
                ((usuario == null ? "" : usuario) + ":" + (clave == null ? "" : clave))
                        .getBytes(StandardCharsets.UTF_8));
    }
}
