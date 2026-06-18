package vn.perfidanb.jarbe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import vn.perfidanb.jarbe.asm.AsmStringReplacer;
import vn.perfidanb.jarbe.assembler.AssemblerService;
import vn.perfidanb.jarbe.model.EntryType;
import vn.perfidanb.jarbe.model.ExportManifest;
import vn.perfidanb.jarbe.model.JarEntryData;
import vn.perfidanb.jarbe.model.JarProject;
import vn.perfidanb.jarbe.util.FileTypeUtil;
import vn.perfidanb.jarbe.util.HashUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public final class JarProjectService {
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final AssemblerService assemblerService = new AssemblerService();
    private final AsmStringReplacer stringReplacer = new AsmStringReplacer();

    public JarProject open(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.isRegularFile(path)) {
            throw new IOException("Input file does not exist: " + path);
        }
        JarProject project = new JarProject(path, path.getFileName().toString());
        try (JarFile jarFile = new JarFile(path.toFile(), false)) {
            var entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry jarEntry = entries.nextElement();
                byte[] bytes;
                if (jarEntry.isDirectory()) {
                    bytes = new byte[0];
                } else {
                    try (InputStream in = jarFile.getInputStream(jarEntry)) {
                        bytes = in.readAllBytes();
                    }
                }
                EntryType type = FileTypeUtil.classify(jarEntry.getName(), jarEntry.isDirectory(), bytes);
                project.putEntry(new JarEntryData(jarEntry.getName(), type, jarEntry.isDirectory(), bytes));
            }
        }
        return project;
    }

    public void saveAsJar(JarProject project, Path outputJar) throws IOException {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(outputJar, "outputJar");
        Path parent = outputJar.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(outputJar))) {
            for (JarEntryData entry : project.entries()) {
                JarEntry jarEntry = new JarEntry(entry.path());
                out.putNextEntry(jarEntry);
                if (!entry.directory()) {
                    out.write(entry.bytes());
                }
                out.closeEntry();
            }
        }
    }

    public void exportProject(JarProject project, Path outputDir) throws IOException {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(outputDir, "outputDir");
        recreateDirectory(outputDir);

        Path classesDir = outputDir.resolve("classes");
        Path resourcesDir = outputDir.resolve("resources");
        Files.createDirectories(classesDir);
        Files.createDirectories(resourcesDir);

        ExportManifest manifest = new ExportManifest();
        manifest.setSourceName(project.displayName());

        for (JarEntryData entry : project.entries()) {
            ExportManifest.ExportedEntry exported = new ExportManifest.ExportedEntry();
            exported.setPath(entry.path());
            exported.setType(entry.type().name());
            exported.setOriginalSha1(HashUtil.sha1(entry.bytes()));
            if (entry.directory()) {
                manifest.getEntries().add(exported);
                continue;
            }
            if (entry.type() == EntryType.CLASS) {
                String classFile = "classes/" + entry.path();
                String jasmFile = classFile + ".jasm";
                Path classPath = safeResolve(outputDir, classFile);
                Path jasmPath = safeResolve(outputDir, jasmFile);
                Files.createDirectories(classPath.getParent());
                Files.createDirectories(jasmPath.getParent());
                Files.write(classPath, entry.bytes());
                String jasm = assemblerService.disassemble(entry.bytes());
                Files.writeString(jasmPath, jasm, StandardCharsets.UTF_8);
                exported.setFile(classFile);
                exported.setJasmFile(jasmFile);
                exported.setJasmSha1(HashUtil.sha1(jasm.getBytes(StandardCharsets.UTF_8)));
            } else {
                String resourceFile = "resources/" + entry.path();
                Path resourcePath = safeResolve(outputDir, resourceFile);
                Files.createDirectories(resourcePath.getParent());
                Files.write(resourcePath, entry.bytes());
                exported.setFile(resourceFile);
            }
            manifest.getEntries().add(exported);
        }
        mapper.writeValue(outputDir.resolve("project.json").toFile(), manifest);
    }

    public void assembleExportedProject(Path projectDir, Path outputJar, Integer targetJava) throws IOException {
        Path manifestPath = projectDir.resolve("project.json");
        if (!Files.isRegularFile(manifestPath)) {
            throw new IOException("Missing project.json in " + projectDir);
        }
        ExportManifest manifest = mapper.readValue(manifestPath.toFile(), ExportManifest.class);
        JarProject project = new JarProject(projectDir, manifest.getSourceName() == null ? "project" : manifest.getSourceName());
        for (ExportManifest.ExportedEntry exported : manifest.getEntries()) {
            EntryType type = EntryType.valueOf(exported.getType());
            if (type == EntryType.DIRECTORY) {
                project.putEntry(new JarEntryData(exported.getPath(), type, true, new byte[0]));
                continue;
            }
            byte[] bytes;
            if (type == EntryType.CLASS) {
                Path classPath = safeResolve(projectDir, exported.getFile());
                byte[] original = Files.readAllBytes(classPath);
                Path jasmPath = safeResolve(projectDir, exported.getJasmFile());
                String jasm = Files.readString(jasmPath, StandardCharsets.UTF_8);
                String currentJasmHash = HashUtil.sha1(jasm.getBytes(StandardCharsets.UTF_8));
                if (targetJava != null || !currentJasmHash.equals(exported.getJasmSha1())) {
                    bytes = assemblerService.assemble(jasm, original, targetJava);
                } else {
                    bytes = original;
                }
            } else {
                bytes = Files.readAllBytes(safeResolve(projectDir, exported.getFile()));
            }
            project.putEntry(new JarEntryData(exported.getPath(), type, false, bytes));
        }
        saveAsJar(project, outputJar);
    }

    public int replaceString(JarProject project, String find, String replacement, Integer targetJava) {
        if (find == null || find.isEmpty()) {
            throw new IllegalArgumentException("Find text must not be empty");
        }
        int changed = 0;
        for (JarEntryData entry : project.entries()) {
            if (entry.directory()) {
                continue;
            }
            if (entry.type() == EntryType.CLASS) {
                byte[] before = entry.bytes();
                byte[] after = stringReplacer.replace(before, find, replacement, targetJava);
                if (!java.util.Arrays.equals(before, after)) {
                    entry.updateBytes(after);
                    changed++;
                }
            } else if (entry.type() == EntryType.TEXT_RESOURCE) {
                String text = new String(entry.bytes(), StandardCharsets.UTF_8);
                if (text.contains(find)) {
                    entry.updateBytes(text.replace(find, replacement).getBytes(StandardCharsets.UTF_8));
                    changed++;
                }
            }
        }
        return changed;
    }

    private static void recreateDirectory(Path outputDir) throws IOException {
        if (Files.exists(outputDir)) {
            try (var walk = Files.walk(outputDir)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        Files.createDirectories(outputDir);
    }

    private static Path safeResolve(Path root, String relative) throws IOException {
        Path normalized = root.resolve(relative.replace('\\', '/')).normalize();
        if (!normalized.startsWith(root.normalize())) {
            throw new IOException("Unsafe archive path: " + relative);
        }
        return normalized;
    }
}
