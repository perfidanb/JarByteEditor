package vn.perfidanb.jarbe;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import vn.perfidanb.jarbe.assembler.AssemblerService;
import vn.perfidanb.jarbe.model.EntryType;
import vn.perfidanb.jarbe.model.JarEntryData;
import vn.perfidanb.jarbe.model.JarProject;
import vn.perfidanb.jarbe.model.TranslationCandidate;
import vn.perfidanb.jarbe.service.TranslationEngine;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationEngineTest {
    @Test
    void previewsAndAppliesResourceAndClassTranslations() throws Exception {
        JarProject project = new JarProject(null, "demo.jar");
        project.putEntry(new JarEntryData("plugin.yml", EntryType.TEXT_RESOURCE, false,
                "name: Demo\ndescription: Hello World\nquote: '\n".getBytes(StandardCharsets.UTF_8)));
        project.putEntry(new JarEntryData("com/example/Main.class", EntryType.CLASS, false,
                helloClass("Hello World")));

        TranslationEngine engine = new TranslationEngine((texts, source, target) ->
                texts.stream().map(text -> "VI " + text).toList());
        List<TranslationCandidate> preview = engine.preview(project, "en", "vi");

        assertFalse(preview.isEmpty());
        engine.apply(project, preview);

        String resource = new String(project.find("plugin.yml").orElseThrow().bytes(), StandardCharsets.UTF_8);
        assertTrue(resource.contains("VI Hello World"));
        String classJasm = new AssemblerService().disassemble(project.find("com/example/Main.class").orElseThrow().bytes());
        assertTrue(classJasm.contains("\"VI Hello World\""));
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
