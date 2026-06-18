package vn.perfidanb.jarbe.util;

import vn.perfidanb.jarbe.model.EntryType;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class FileTypeUtil {
    private FileTypeUtil() {
    }

    public static EntryType classify(String path, boolean directory, byte[] bytes) {
        if (directory) {
            return EntryType.DIRECTORY;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".class")) {
            return EntryType.CLASS;
        }
        if (isTextPath(lower) || looksText(bytes)) {
            return EntryType.TEXT_RESOURCE;
        }
        return EntryType.BINARY_RESOURCE;
    }

    public static EntryType classifySample(String path, boolean directory, byte[] sampleBytes) {
        if (directory) {
            return EntryType.DIRECTORY;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".class")) {
            return EntryType.CLASS;
        }
        if (isTextPath(lower) || looksText(sampleBytes)) {
            return EntryType.TEXT_RESOURCE;
        }
        return EntryType.BINARY_RESOURCE;
    }

    public static boolean isTextPath(String lowerPath) {
        return lowerPath.endsWith(".yml")
                || lowerPath.endsWith(".yaml")
                || lowerPath.endsWith(".json")
                || lowerPath.endsWith(".txt")
                || lowerPath.endsWith(".xml")
                || lowerPath.endsWith(".properties")
                || lowerPath.endsWith(".mf")
                || lowerPath.endsWith(".csv")
                || lowerPath.endsWith(".md")
                || lowerPath.endsWith(".html")
                || lowerPath.endsWith(".css")
                || lowerPath.endsWith(".js");
    }

    public static boolean looksText(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return true;
        }
        int sample = Math.min(bytes.length, 4096);
        int control = 0;
        for (int i = 0; i < sample; i++) {
            int value = bytes[i] & 0xff;
            if (value == 0) {
                return false;
            }
            if (value < 0x09 || (value > 0x0d && value < 0x20)) {
                control++;
            }
        }
        try {
            new String(bytes, 0, sample, StandardCharsets.UTF_8);
        } catch (RuntimeException ignored) {
            return false;
        }
        return control < sample / 20;
    }

    public static boolean isBinaryPreviewPath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp")
                || lower.endsWith(".class")
                || lower.endsWith(".dat")
                || lower.endsWith(".nbt");
    }
}
