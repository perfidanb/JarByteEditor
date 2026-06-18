package vn.perfidanb.jarbe.editor;

import vn.perfidanb.jarbe.assembler.AssemblerService;
import vn.perfidanb.jarbe.model.EntryType;
import vn.perfidanb.jarbe.model.JarEntryData;
import vn.perfidanb.jarbe.model.JarProject;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class EditorSession {
    private final JarProject project;
    private final AssemblerService assemblerService = new AssemblerService();

    public EditorSession(JarProject project) {
        this.project = Objects.requireNonNull(project, "project");
    }

    public JarProject project() {
        return project;
    }

    public String editableText(JarEntryData entry) {
        if (entry.type() == EntryType.CLASS) {
            return assemblerService.disassemble(entry.bytes());
        }
        if (entry.type() == EntryType.TEXT_RESOURCE) {
            return new String(entry.bytes(), StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException("Entry is not editable as text: " + entry.path());
    }

    public void applyText(JarEntryData entry, String text, Integer targetJava) {
        if (entry.type() == EntryType.CLASS) {
            entry.updateBytes(assemblerService.assemble(text, entry.bytes(), targetJava));
        } else if (entry.type() == EntryType.TEXT_RESOURCE) {
            entry.updateBytes(text.getBytes(StandardCharsets.UTF_8));
        } else {
            throw new IllegalArgumentException("Entry is not editable as text: " + entry.path());
        }
    }
}
