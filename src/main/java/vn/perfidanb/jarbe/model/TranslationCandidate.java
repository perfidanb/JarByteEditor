package vn.perfidanb.jarbe.model;

import java.util.Objects;

public final class TranslationCandidate {
    private final String path;
    private final String location;
    private final EntryType entryType;
    private final String originalText;
    private final String translatedText;

    public TranslationCandidate(String path, String location, EntryType entryType, String originalText, String translatedText) {
        this.path = Objects.requireNonNull(path, "path");
        this.location = Objects.requireNonNull(location, "location");
        this.entryType = Objects.requireNonNull(entryType, "entryType");
        this.originalText = Objects.requireNonNull(originalText, "originalText");
        this.translatedText = Objects.requireNonNull(translatedText, "translatedText");
    }

    public String path() {
        return path;
    }

    public String location() {
        return location;
    }

    public EntryType entryType() {
        return entryType;
    }

    public String originalText() {
        return originalText;
    }

    public String translatedText() {
        return translatedText;
    }

    public boolean changed() {
        return !originalText.equals(translatedText);
    }
}
