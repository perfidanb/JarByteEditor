package vn.perfidanb.jarbe.model;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Objects;

public final class JarEntryData {
    public interface EntryByteSource {
        byte[] read() throws IOException;
    }

    private final String path;
    private final EntryType type;
    private final boolean directory;
    private final EntryByteSource byteSource;
    private final byte[] sampleBytes;
    private long byteSize;
    private byte[] originalBytes;
    private byte[] bytes;
    private boolean loaded;
    private boolean modified;

    public JarEntryData(String path, EntryType type, boolean directory, byte[] bytes) {
        this.path = Objects.requireNonNull(path, "path");
        this.type = Objects.requireNonNull(type, "type");
        this.directory = directory;
        this.byteSource = null;
        byte[] safeBytes = bytes == null ? new byte[0] : bytes;
        this.sampleBytes = sampleOf(safeBytes);
        this.originalBytes = safeBytes;
        this.bytes = safeBytes;
        this.byteSize = safeBytes.length;
        this.loaded = true;
    }

    public JarEntryData(String path, EntryType type, boolean directory, long byteSize,
                        byte[] sampleBytes, EntryByteSource byteSource) {
        this.path = Objects.requireNonNull(path, "path");
        this.type = Objects.requireNonNull(type, "type");
        this.directory = directory;
        this.byteSource = byteSource;
        this.sampleBytes = sampleBytes == null ? new byte[0] : sampleBytes;
        this.byteSize = Math.max(0, byteSize);
        if (directory) {
            this.originalBytes = new byte[0];
            this.bytes = originalBytes;
            this.byteSize = 0;
            this.loaded = true;
        }
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

    public synchronized byte[] originalBytes() {
        ensureLoaded();
        return originalBytes;
    }

    public synchronized byte[] bytes() {
        ensureLoaded();
        return bytes;
    }

    public synchronized byte[] readBytesOnce() {
        if (loaded) {
            return bytes;
        }
        if (byteSource == null) {
            return new byte[0];
        }
        try {
            byte[] loadedBytes = byteSource.read();
            return loadedBytes == null ? new byte[0] : loadedBytes;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load archive entry: " + path, e);
        }
    }

    public synchronized byte[] sampleBytes() {
        if (loaded) {
            return sampleOf(bytes);
        }
        return sampleBytes;
    }

    public synchronized boolean loaded() {
        return loaded;
    }

    public synchronized void updateBytes(byte[] updatedBytes) {
        ensureLoaded();
        this.bytes = Objects.requireNonNull(updatedBytes, "updatedBytes");
        this.byteSize = this.bytes.length;
        this.modified = !Arrays.equals(originalBytes, this.bytes);
    }

    public synchronized boolean modified() {
        return modified;
    }

    public synchronized int size() {
        return byteSize > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) byteSize;
    }

    public synchronized long byteSize() {
        return byteSize;
    }

    public synchronized void markSaved() {
        ensureLoaded();
        this.originalBytes = bytes;
        this.byteSize = bytes.length;
        this.modified = false;
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        if (byteSource == null) {
            throw new IllegalStateException("No byte source for archive entry: " + path);
        }
        try {
            byte[] loadedBytes = byteSource.read();
            this.originalBytes = loadedBytes == null ? new byte[0] : loadedBytes;
            this.bytes = originalBytes;
            this.byteSize = originalBytes.length;
            this.loaded = true;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load archive entry: " + path, e);
        }
    }

    private static byte[] sampleOf(byte[] source) {
        int length = Math.min(source == null ? 0 : source.length, 4096);
        if (length == 0) {
            return new byte[0];
        }
        return Arrays.copyOf(source, length);
    }
}
