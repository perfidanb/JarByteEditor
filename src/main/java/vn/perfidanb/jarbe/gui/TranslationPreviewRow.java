package vn.perfidanb.jarbe.gui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import vn.perfidanb.jarbe.model.TranslationCandidate;

final class TranslationPreviewRow {
    private final BooleanProperty selected = new SimpleBooleanProperty(true);
    private final StringProperty path = new SimpleStringProperty();
    private final StringProperty location = new SimpleStringProperty();
    private final StringProperty original = new SimpleStringProperty();
    private final StringProperty translated = new SimpleStringProperty();
    private final TranslationCandidate candidate;

    TranslationPreviewRow(TranslationCandidate candidate) {
        this.candidate = candidate;
        path.set(candidate.path());
        location.set(candidate.location());
        original.set(candidate.originalText());
        translated.set(candidate.translatedText());
    }

    BooleanProperty selectedProperty() {
        return selected;
    }

    boolean selected() {
        return selected.get();
    }

    StringProperty pathProperty() {
        return path;
    }

    StringProperty locationProperty() {
        return location;
    }

    StringProperty originalProperty() {
        return original;
    }

    StringProperty translatedProperty() {
        return translated;
    }

    TranslationCandidate candidate() {
        return candidate;
    }
}
