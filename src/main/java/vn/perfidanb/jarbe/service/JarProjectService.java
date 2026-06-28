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
import vn.perfidanb.jarbe.service.ApktoolService;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

public final class JarProjectService {
    private static final int ENTRY_SAMPLE_BYTES = 4096;

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final AssemblerService assemblerService = new AssemblerService();
    private final AsmStringReplacer stringReplacer = new AsmStringReplacer();

    public JarProject open(Path path) throws IOException {
        return open(path, OpenProgress.noop());
    }

    public JarProject open(Path path, OpenProgress progress) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(progress, "progress");
        if (!Files.isRegularFile(path)) {
            throw new IOException("Input file does not exist: " + path);
        }
        JarProject project = new JarProject(path, path.getFileName().toString());
        try (JarFile jarFile = new JarFile(path.toFile(), false)) {
            var entries = jarFile.entries();
            int total = jarFile.size();
            int completed = 0;
            while (entries.hasMoreElements()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedIOException("Open cancelled");
                }
                JarEntry jarEntry = entries.nextElement();
                if (jarEntry.isDirectory()) {
                    project.putEntry(new JarEntryData(jarEntry.getName(), EntryType.DIRECTORY, true, new byte[0]));
                } else {
                    String entryName = jarEntry.getName();
                    long size = jarEntry.getSize();
                    byte[] sample = readSample(jarFile, jarEntry);
                    EntryType type = FileTypeUtil.classifySample(entryName, false, sample);
                    project.putEntry(new JarEntryData(entryName, type, false, size < 0 ? sample.length : size,
                            sample, () -> readJarEntry(path, entryName)));
                }
                completed++;
                progress.update(completed, total, jarEntry.getName(), Math.max(0, jarEntry.getSize()));
            }
        }
        return project;
    }

    private void runProcessWithProgress(ProcessBuilder pb, java.io.File destDir, java.util.function.Consumer<String> onProgress) throws Exception {
        Process process = pb.start();
        
        Thread readerThread = new Thread(() -> {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                long lastUpdate = System.currentTimeMillis();
                String line;
                while ((line = reader.readLine()) != null) {
                    long now = System.currentTimeMillis();
                    if (now - lastUpdate > 50) { // Throttle updates to avoid freezing JavaFX
                        onProgress.accept(line);
                        lastUpdate = now;
                    }
                }
            } catch (IOException e) {
            }
        });
        readerThread.setDaemon(true);
        readerThread.start();
        
        Thread watchdog = new Thread(() -> {
            try {
                java.lang.management.OperatingSystemMXBean osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
                com.sun.management.OperatingSystemMXBean sunOsBean = (osBean instanceof com.sun.management.OperatingSystemMXBean) ? 
                    (com.sun.management.OperatingSystemMXBean) osBean : null;
                
                while (process.isAlive()) {
                    if (destDir != null) {
                        long freeDiskMb = destDir.getUsableSpace() / (1024 * 1024);
                        if (freeDiskMb < 500) {
                            process.destroyForcibly();
                            onProgress.accept("CRITICAL WARNING: System Disk space critically low (" + freeDiskMb + "MB). Auto-Kill triggered to prevent Disk Overflow!");
                            break;
                        }
                    }

                    if (sunOsBean != null) {
                        long freeMemMb = sunOsBean.getFreeMemorySize() / (1024 * 1024);
                        if (freeMemMb < 200) {
                            process.destroyForcibly();
                            onProgress.accept("CRITICAL WARNING: System free memory critically low (" + freeMemMb + "MB). Auto-Kill triggered to prevent OS crash!");
                            break;
                        }
                    }
                    Thread.sleep(1000);
                }
            } catch (Exception e) {}
        });
        watchdog.setDaemon(true);
        watchdog.start();
        
        try {
            while (process.isAlive()) {
                if (Thread.currentThread().isInterrupted()) {
                    process.destroyForcibly();
                    throw new InterruptedIOException("Cancelled by user");
                }
                Thread.sleep(50);
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new IOException("Apktool failed with exit code: " + exitCode + ". See console for details.");
            }
        } finally {
            process.destroyForcibly();
        }
    }

    public JarProject openApk(Path apkPath, OpenProgress progress) throws Exception {
        Objects.requireNonNull(apkPath, "apkPath");
        Objects.requireNonNull(progress, "progress");

        // 1. Create a temp directory
        Path tempDir = Files.createTempDirectory("jarbe_apk_");
        tempDir.toFile().deleteOnExit();

        // 2. Decode APK (Resources only, lazy load DEX)
        progress.update(0, 100, "Decoding APK Resources (this may take a while)...", 0);
        ProcessBuilder pb = ApktoolService.decodeResourcesOnly(apkPath.toFile(), tempDir.toFile());
        runProcessWithProgress(pb, tempDir.toFile(), line -> progress.update(0, 100, line, 0));

        // 3. Scan directory and build JarProject
        progress.update(50, 100, "Scanning directory...", 0);
        JarProject project = new JarProject(apkPath, apkPath.getFileName().toString(), tempDir);
        
        // 3.5 Auto-decode DEX files sequentially to prevent disk/CPU overload
        // REVERTED: Auto-decoding all DEX files causes gigabytes of disk usage and 100% disk load.
        // Instead, we will parse the DEX files into a virtual in-memory tree below!
        
        List<Path> allFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(tempDir)) {
            stream.filter(Files::isRegularFile).forEach(allFiles::add);
        }
        
        int total = allFiles.size();
        int completed = 0;
        for (Path file : allFiles) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("Open cancelled");
            }
            
            String entryName = tempDir.relativize(file).toString().replace('\\', '/');
            String lower = entryName.toLowerCase(java.util.Locale.ROOT);
            EntryType type;
            byte[] sample = new byte[0];
            
            if (lower.endsWith(".dex") && file.getFileName().toString().startsWith("classes")) {
                try {
                    org.jf.dexlib2.iface.DexFile dexFile = org.jf.dexlib2.DexFileFactory.loadDexFile(file.toFile(), org.jf.dexlib2.Opcodes.getDefault());
                    org.jf.baksmali.BaksmaliOptions options = new org.jf.baksmali.BaksmaliOptions();
                    
                    String smaliPrefix;
                    if (file.getFileName().toString().equals("classes.dex")) {
                        smaliPrefix = "smali";
                    } else {
                        String name = file.getFileName().toString();
                        smaliPrefix = "smali_" + name.substring(0, name.lastIndexOf('.'));
                    }
                    
                    int dexCompleted = 0;
                    for (org.jf.dexlib2.iface.ClassDef classDef : dexFile.getClasses()) {
                        if (Thread.currentThread().isInterrupted()) {
                            throw new InterruptedIOException("Open cancelled");
                        }
                        String classType = classDef.getType(); // e.g. "Ljava/lang/String;"
                        if (classType.startsWith("L") && classType.endsWith(";")) {
                            classType = classType.substring(1, classType.length() - 1);
                        }
                        String virtualPath = smaliPrefix + "/" + classType + ".smali";
                        
                        project.putEntry(new JarEntryData(virtualPath, EntryType.TEXT_RESOURCE, false, 0,
                                new byte[0], () -> {
                            try {
                                java.io.StringWriter stringWriter = new java.io.StringWriter();
                                org.jf.baksmali.formatter.BaksmaliWriter writer = new org.jf.baksmali.formatter.BaksmaliWriter(
                                        stringWriter,
                                        options.implicitReferences ? classDef.getType() : null);
                                org.jf.baksmali.Adaptors.ClassDefinition classDefinition = new org.jf.baksmali.Adaptors.ClassDefinition(options, classDef);
                                classDefinition.writeTo(writer);
                                return stringWriter.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                            } catch (Exception e) {
                                throw new IOException("Error disassembling class: " + e.getMessage(), e);
                            }
                        }));
                        dexCompleted++;
                        if (dexCompleted % 500 == 0) {
                            progress.update(50 + (int)(50.0 * completed / total), 100, virtualPath, 0);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Failed to parse dex: " + file);
                }
                type = EntryType.BINARY_RESOURCE;
            } else if (lower.endsWith(".class")) {
                type = EntryType.CLASS;
            } else if (vn.perfidanb.jarbe.util.FileTypeUtil.isTextPath(lower)) {
                type = EntryType.TEXT_RESOURCE;
            } else if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".ogg") || lower.endsWith(".webp") || lower.endsWith(".dex") || lower.endsWith(".arsc") || lower.endsWith(".ttf") || lower.endsWith(".mp3") || lower.endsWith(".mp4") || lower.endsWith(".wav")) {
                type = EntryType.BINARY_RESOURCE;
            } else {
                sample = readSampleFromPath(file);
                type = vn.perfidanb.jarbe.util.FileTypeUtil.looksText(sample) ? EntryType.TEXT_RESOURCE : EntryType.BINARY_RESOURCE;
            }
            
            long size = Files.size(file);
            
            project.putEntry(new JarEntryData(entryName, type, false, size,
                    sample, () -> Files.readAllBytes(file)));
            completed++;
            progress.update(50 + (int)(50.0 * completed / total), 100, entryName, size);
        }
        
        return project;
    }



    private byte[] readSampleFromPath(Path file) throws IOException {
        try (InputStream is = Files.newInputStream(file)) {
            byte[] buf = new byte[8192];
            int read = is.read(buf);
            if (read < 0) return new byte[0];
            if (read == buf.length) return buf;
            byte[] sample = new byte[read];
            System.arraycopy(buf, 0, sample, 0, read);
            return sample;
        }
    }

    public void saveAsJar(JarProject project, Path outputJar) throws IOException {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(outputJar, "outputJar");
        Path parent = outputJar.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (JarFile sourceJar = sourceJar(project, outputJar);
             JarOutputStream out = new JarOutputStream(Files.newOutputStream(outputJar))) {
            for (JarEntryData entry : project.entries()) {
                JarEntry jarEntry = new JarEntry(entry.path());
                out.putNextEntry(jarEntry);
                if (!entry.directory()) {
                    JarEntry sourceEntry = sourceJar == null || entry.modified() ? null : sourceJar.getJarEntry(entry.path());
                    if (sourceEntry != null) {
                        try (InputStream in = sourceJar.getInputStream(sourceEntry)) {
                            in.transferTo(out);
                        }
                    } else {
                        out.write(entry.bytes());
                    }
                }
                out.closeEntry();
            }
        }
    }

    public void saveApk(JarProject project, Path outputApk, java.util.function.Consumer<String> onProgress) throws Exception {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(outputApk, "outputApk");
        Path extractedDir = project.extractedDir().orElseThrow(() -> new IllegalArgumentException("Not an APK project"));
        
        java.util.Map<String, java.util.List<JarEntryData>> modifiedSmaliFiles = new java.util.HashMap<>();
        java.util.List<JarEntryData> otherModifiedFiles = new java.util.ArrayList<>();
        
        for (JarEntryData entry : project.modifiedEntries()) {
            if (entry.directory()) continue;
            
            String path = entry.path();
            if (path.startsWith("smali/") || path.contains("/smali/") || path.startsWith("smali_")) {
                String prefix = path.substring(0, path.indexOf('/')); // e.g. "smali" or "smali_classes2"
                String dexFileName = prefix.equals("smali") ? "classes.dex" : prefix.substring(6) + ".dex";
                modifiedSmaliFiles.computeIfAbsent(dexFileName, k -> new java.util.ArrayList<>()).add(entry);
            } else {
                otherModifiedFiles.add(entry);
            }
        }
        
        // 1. Sync non-smali modified files to extractedDir
        for (JarEntryData entry : otherModifiedFiles) {
            Path dest = extractedDir.resolve(entry.path());
            Files.createDirectories(dest.getParent());
            Files.write(dest, entry.bytes());
            entry.markSaved();
        }
        
        // 2. Re-assemble modified DEX files
        for (java.util.Map.Entry<String, java.util.List<JarEntryData>> mapEntry : modifiedSmaliFiles.entrySet()) {
            String dexFileName = mapEntry.getKey();
            Path dexPath = extractedDir.resolve(dexFileName);
            
            if (!Files.exists(dexPath)) {
                onProgress.accept("WARNING: Cannot re-assemble " + dexFileName + " because original DEX is missing!");
                continue;
            }
            
            onProgress.accept("Re-assembling " + dexFileName + "...");
            Path tempSmaliDir = Files.createTempDirectory("jarbe_smali_build_");
            try {
                // a. Disassemble original dex
                org.jf.dexlib2.iface.DexFile dexFile = org.jf.dexlib2.DexFileFactory.loadDexFile(dexPath.toFile(), org.jf.dexlib2.Opcodes.getDefault());
                org.jf.baksmali.BaksmaliOptions options = new org.jf.baksmali.BaksmaliOptions();
                org.jf.baksmali.Baksmali.disassembleDexFile(dexFile, tempSmaliDir.toFile(), Math.max(1, (int)(Runtime.getRuntime().availableProcessors() * 0.5)), options);
                
                // b. Overwrite modified files
                for (JarEntryData entry : mapEntry.getValue()) {
                    String path = entry.path();
                    String smaliRelativePath = path.substring(path.indexOf('/') + 1); // strip "smali/" or "smali_classes2/"
                    Path dest = tempSmaliDir.resolve(smaliRelativePath);
                    Files.createDirectories(dest.getParent());
                    Files.write(dest, entry.bytes());
                    entry.markSaved();
                }
                
                // c. Assemble back to dex
                org.jf.smali.SmaliOptions smaliOptions = new org.jf.smali.SmaliOptions();
                boolean success = org.jf.smali.Smali.assemble(smaliOptions, tempSmaliDir.toFile().getAbsolutePath(), dexPath.toFile().getAbsolutePath());
                if (!success) {
                    throw new Exception("Failed to re-assemble " + dexFileName);
                }
            } finally {
                // Delete temp directory
                try (java.util.stream.Stream<Path> stream = Files.walk(tempSmaliDir)) {
                    stream.sorted(java.util.Comparator.reverseOrder())
                          .map(Path::toFile)
                          .forEach(java.io.File::delete);
                } catch (IOException ignored) {}
            }
        }
        
        // 3. Build APK
        onProgress.accept("Building final APK...");
        ProcessBuilder pb = ApktoolService.build(extractedDir.toFile(), outputApk.toFile());
        runProcessWithProgress(pb, null, onProgress);
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
                byte[] before = entry.readBytesOnce();
                byte[] after = stringReplacer.replace(before, find, replacement, targetJava);
                if (!java.util.Arrays.equals(before, after)) {
                    entry.updateBytes(after);
                    changed++;
                }
            } else if (entry.type() == EntryType.TEXT_RESOURCE) {
                String text = new String(entry.readBytesOnce(), StandardCharsets.UTF_8);
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

    private static byte[] readSample(JarFile jarFile, JarEntry jarEntry) throws IOException {
        try (InputStream in = jarFile.getInputStream(jarEntry)) {
            return in.readNBytes(ENTRY_SAMPLE_BYTES);
        }
    }

    private static byte[] readJarEntry(Path jarPath, String entryName) throws IOException {
        try (JarFile jarFile = new JarFile(jarPath.toFile(), false)) {
            JarEntry entry = jarFile.getJarEntry(entryName);
            if (entry == null) {
                throw new IOException("Missing archive entry: " + entryName);
            }
            try (InputStream in = jarFile.getInputStream(entry)) {
                return in.readAllBytes();
            }
        }
    }

    private static JarFile sourceJar(JarProject project, Path outputJar) throws IOException {
        Path source = project.sourcePath().orElse(null);
        if (source == null || !Files.isRegularFile(source)) {
            return null;
        }
        Path sourcePath = source.toAbsolutePath().normalize();
        Path outputPath = outputJar.toAbsolutePath().normalize();
        if (sourcePath.equals(outputPath)) {
            throw new IOException("Choose a different output file. Saving over the opened jar is not safe.");
        }
        return new JarFile(source.toFile(), false);
    }

    public interface OpenProgress {
        void update(int completed, int total, String entryName, long entrySize);

        static OpenProgress noop() {
            return (completed, total, entryName, entrySize) -> {
            };
        }
    }
}
