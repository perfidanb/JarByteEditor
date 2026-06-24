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
    private final javafx.collections.ObservableList<java.io.File> userDependencies = javafx.collections.FXCollections.observableArrayList();

    private final vn.perfidanb.jarbe.service.JavaDecompilerService javaDecompilerService = new vn.perfidanb.jarbe.service.JavaDecompilerService();
    private final vn.perfidanb.jarbe.service.JavaCompilerService javaCompilerService = new vn.perfidanb.jarbe.service.JavaCompilerService();

    public EditorSession(JarProject project) {
        this.project = Objects.requireNonNull(project, "project");
    }

    public JarProject project() {
        return project;
    }

    public javafx.collections.ObservableList<java.io.File> getUserDependencies() {
        return userDependencies;
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

    public String javaEditableText(JarEntryData entry) {
        if (entry.type() == EntryType.CLASS) {
            return javaDecompilerService.decompile(entry);
        }
        throw new IllegalArgumentException("Entry is not editable as Java: " + entry.path());
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

    public void applyJavaText(JarEntryData entry, String javaCode) throws Exception {
        if (entry.type() == EntryType.CLASS) {
            String className = entry.path().replace(".class", "").replace('/', '.');
            byte[] compiledBytes = javaCompilerService.compile(className, javaCode, project, project.sourcePath().orElse(null), userDependencies);
            entry.updateBytes(compiledBytes);
        } else {
            throw new IllegalArgumentException("Entry is not compilable as Java: " + entry.path());
        }
    }
}
