package vn.perfidanb.jarbe.model;

import java.util.Arrays;
import java.util.Objects;

public final class JarEntryData {
    private final String path;
    private final EntryType type;
    private final boolean directory;
    private byte[] originalBytes;
    private byte[] bytes;

    public JarEntryData(String path, EntryType type, boolean directory, byte[] bytes) {
        this.path = Objects.requireNonNull(path, "path");
        this.type = Objects.requireNonNull(type, "type");
        this.directory = directory;
        this.originalBytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
        this.bytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
    }

    public String path() {
        return path;
    }

    public EntryType type() {
        return type;
    }

    public boolean directory() {
        return directory;
    }

    public byte[] originalBytes() {
        return Arrays.copyOf(originalBytes, originalBytes.length);
    }

    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    public void updateBytes(byte[] updatedBytes) {
        this.bytes = Arrays.copyOf(Objects.requireNonNull(updatedBytes, "updatedBytes"), updatedBytes.length);
    }

    public boolean modified() {
        return !Arrays.equals(originalBytes, bytes);
    }

    public int size() {
        return bytes.length;
    }

    public void markSaved() {
        this.originalBytes = Arrays.copyOf(bytes, bytes.length);
    }
}
