package vn.perfidanb.jarbe.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashUtil {
    private HashUtil() {
    }

    public static String sha1(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hashed = digest.digest(bytes);
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 digest is not available", e);
        }
    }
}
