package vn.perfidanb.jarbe.service;

import vn.perfidanb.jarbe.model.JarProject;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class JavaCompilerService {

    public byte[] compile(String className, String sourceCode, JarProject project, Path jarPath, List<File> extraDependencies) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("System JavaCompiler not found. Make sure you are running with a JDK, not a JRE.");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        
        List<String> options = new ArrayList<>();
        options.addAll(Arrays.asList("-classpath", buildClasspath(jarPath, extraDependencies)));

        StringSourceJavaFileObject sourceFile = new StringSourceJavaFileObject(className, sourceCode);
        Iterable<? extends JavaFileObject> compilationUnits = Collections.singletonList(sourceFile);

        try (MemoryJavaFileManager fileManager = new MemoryJavaFileManager(compiler.getStandardFileManager(diagnostics, null, null))) {
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null, fileManager, diagnostics, options, null, compilationUnits);

            boolean success = task.call();
            if (!success) {
                StringBuilder errorMsg = new StringBuilder("Compilation failed:\n");
                for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                    errorMsg.append(diagnostic.getMessage(null)).append("\n");
                }
                throw new Exception(errorMsg.toString());
            }

            return fileManager.getCompiledBytes();
        }
    }

    private String buildClasspath(Path currentJarPath, List<File> extraDependencies) {
        List<String> classpathElements = new ArrayList<>();
        
        // Add current jar
        if (currentJarPath != null && Files.exists(currentJarPath)) {
            classpathElements.add(currentJarPath.toAbsolutePath().toString());
        }

        // Add extra dependencies
        if (extraDependencies != null) {
            for (File dep : extraDependencies) {
                if (dep.exists()) {
                    classpathElements.add(dep.getAbsolutePath());
                }
            }
        }

        // Check for libs folder in the same directory as the jar
        if (currentJarPath != null && currentJarPath.getParent() != null) {
            Path libsDir = currentJarPath.getParent().resolve("libs");
            if (Files.exists(libsDir) && Files.isDirectory(libsDir)) {
                try {
                    Files.list(libsDir)
                            .filter(p -> p.toString().toLowerCase().endsWith(".jar"))
                            .forEach(p -> classpathElements.add(p.toAbsolutePath().toString()));
                } catch (IOException e) {
                    // Ignore
                }
            }
        }

        return String.join(File.pathSeparator, classpathElements);
    }

    private static class StringSourceJavaFileObject extends SimpleJavaFileObject {
        private final String code;

        protected StringSourceJavaFileObject(String name, String code) {
            super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    private static class MemoryJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {
        private ClassByteArrayOutputStream outputStream;

        protected MemoryJavaFileManager(JavaFileManager fileManager) {
            super(fileManager);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
            outputStream = new ClassByteArrayOutputStream(className);
            return outputStream;
        }

        public byte[] getCompiledBytes() {
            if (outputStream == null) {
                return null;
            }
            return outputStream.getBytes();
        }
    }

    private static class ClassByteArrayOutputStream extends SimpleJavaFileObject {
        private final ByteArrayOutputStream bos = new ByteArrayOutputStream();

        protected ClassByteArrayOutputStream(String name) {
            super(URI.create("bytes:///" + name.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
        }

        @Override
        public OutputStream openOutputStream() {
            return bos;
        }

        public byte[] getBytes() {
            return bos.toByteArray();
        }
    }
}
