package vn.perfidanb.jarbe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import vn.perfidanb.jarbe.model.JarEntryData;
import vn.perfidanb.jarbe.model.JarProject;
import vn.perfidanb.jarbe.service.JarProjectService;
import vn.perfidanb.jarbe.service.TranslationEngine;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JarProjectServiceLazyLoadTest {
    @TempDir
    Path tempDir;

    @Test
    void opensLargeEntriesLazilyAndStreamsUnchangedEntriesOnSave() throws Exception {
        Path input = tempDir.resolve("large.jar");
        byte[] large = new byte[3 * 1024 * 1024];
        Arrays.fill(large, (byte) 'A');
        try (JarOutputStream out = new JarOutputStream(java.nio.file.Files.newOutputStream(input))) {
            out.putNextEntry(new JarEntry("assets/large.bin"));
            out.write(large);
            out.closeEntry();
        }

        JarProjectService service = new JarProjectService();
        JarProject project = service.open(input);
        JarEntryData entry = project.find("assets/large.bin").orElseThrow();

        assertEquals(large.length, entry.byteSize());
        assertFalse(entry.loaded());
        assertArrayEquals(large, entry.readBytesOnce());
        assertFalse(entry.loaded());

        Path output = tempDir.resolve("copy.jar");
        service.saveAsJar(project, output);

        assertFalse(entry.loaded());
        try (JarFile jar = new JarFile(output.toFile(), false)) {
            assertArrayEquals(large, jar.getInputStream(jar.getJarEntry("assets/large.bin")).readAllBytes());
        }
    }

    @Test
    void translationPreviewScansLazyClassesWithoutCachingThem() throws Exception {
        Path input = tempDir.resolve("strings.jar");
        try (JarOutputStream out = new JarOutputStream(java.nio.file.Files.newOutputStream(input))) {
            out.putNextEntry(new JarEntry("com/example/Main.class"));
            out.write(helloClass("Hello World"));
            out.closeEntry();
        }

        JarProject project = new JarProjectService().open(input);
        JarEntryData entry = project.find("com/example/Main.class").orElseThrow();
        assertFalse(entry.loaded());

        TranslationEngine engine = new TranslationEngine((texts, source, target) ->
                texts.stream().map(text -> "VI " + text).toList());

        assertFalse(engine.preview(project, "en", "vi").isEmpty());
        assertFalse(entry.loaded());
    }

    private static byte[] helloClass(String literal) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Main", null, "java/lang/Object", null);

        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();

        MethodVisitor message = writer.visitMethod(Opcodes.ACC_PUBLIC, "message", "()Ljava/lang/String;", null, null);
        message.visitCode();
        message.visitLdcInsn(literal);
        message.visitInsn(Opcodes.ARETURN);
        message.visitMaxs(1, 1);
        message.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }
}
