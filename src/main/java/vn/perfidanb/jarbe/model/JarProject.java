package vn.perfidanb.jarbe.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class JarProject {
    private final Path sourcePath;
    private final String displayName;
    private final Path extractedDir;
    private final Map<String, JarEntryData> entries = new LinkedHashMap<>();

    public JarProject(Path sourcePath, String displayName) {
        this(sourcePath, displayName, null);
    }

    public JarProject(Path sourcePath, String displayName, Path extractedDir) {
        this.sourcePath = sourcePath;
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.extractedDir = extractedDir;
    }

    public Optional<Path> sourcePath() {
        return Optional.ofNullable(sourcePath);
    }

    public Optional<Path> extractedDir() {
        return Optional.ofNullable(extractedDir);
    }

    public String displayName() {
        return displayName;
    }

    public void putEntry(JarEntryData entry) {
        entries.put(entry.path(), entry);
    }

    public Optional<JarEntryData> find(String path) {
        return Optional.ofNullable(entries.get(path));
    }

    public void removeEntry(String path) {
        entries.remove(path);
    }

    public Collection<JarEntryData> entries() {
        return List.copyOf(entries.values());
    }

    public List<JarEntryData> sortedEntries() {
        return entries.values().stream()
                .sorted((left, right) -> left.path().compareToIgnoreCase(right.path()))
                .toList();
    }

    public List<JarEntryData> classEntries() {
        return entries.values().stream()
                .filter(entry -> entry.type() == EntryType.CLASS)
                .sorted((left, right) -> left.path().compareToIgnoreCase(right.path()))
                .toList();
    }

    public List<JarEntryData> resourceEntries() {
        return entries.values().stream()
                .filter(entry -> entry.type() == EntryType.TEXT_RESOURCE || entry.type() == EntryType.BINARY_RESOURCE)
                .sorted((left, right) -> left.path().compareToIgnoreCase(right.path()))
                .toList();
    }

    public List<JarEntryData> modifiedEntries() {
        List<JarEntryData> modified = new ArrayList<>();
        for (JarEntryData entry : entries.values()) {
            if (entry.modified()) {
                modified.add(entry);
            }
        }
        return modified;
    }

    public void markSaved() {
        entries.values().forEach(JarEntryData::markSaved);
    }
}
