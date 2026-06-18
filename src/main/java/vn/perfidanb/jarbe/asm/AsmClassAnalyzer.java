package vn.perfidanb.jarbe.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import vn.perfidanb.jarbe.model.ClassSummary;
import vn.perfidanb.jarbe.model.FieldSummary;
import vn.perfidanb.jarbe.model.MethodSummary;

import java.util.ArrayList;
import java.util.List;

public final class AsmClassAnalyzer {
    public ClassSummary analyze(byte[] classBytes) {
        ClassNode node = readNode(classBytes);
        List<FieldSummary> fields = new ArrayList<>();
        for (FieldNode field : node.fields) {
            fields.add(new FieldSummary(field.name, field.desc, field.access));
        }

        List<MethodSummary> methods = new ArrayList<>();
        int instructionCount = 0;
        for (MethodNode method : node.methods) {
            List<String> calls = new ArrayList<>();
            int methodInstructionCount = 0;
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction.getOpcode() >= 0) {
                    methodInstructionCount++;
                }
                if (instruction instanceof MethodInsnNode call) {
                    calls.add(call.owner + "." + call.name + call.desc);
                }
            }
            instructionCount += methodInstructionCount;
            methods.add(new MethodSummary(method.name, method.desc, method.access, calls, methodInstructionCount));
        }

        List<String> annotations = new ArrayList<>();
        addAnnotations(annotations, node.visibleAnnotations);
        addAnnotations(annotations, node.invisibleAnnotations);

        return new ClassSummary(
                node.name,
                node.superName,
                List.copyOf(node.interfaces),
                node.version,
                ClassVersion.javaForMajor(node.version),
                node.access,
                fields,
                methods,
                annotations,
                instructionCount
        );
    }

    public ClassNode readNode(byte[] classBytes) {
        int major = ClassVersion.detectMajor(classBytes);
        if (!ClassVersion.supportsMajor(major)) {
            throw new IllegalArgumentException("Unsupported class version: " + major);
        }
        ClassReader reader = new ClassReader(classBytes);
        ClassNode node = new ClassNode();
        reader.accept(node, ClassReader.EXPAND_FRAMES);
        return node;
    }

    private static void addAnnotations(List<String> annotations, List<AnnotationNode> nodes) {
        if (nodes == null) {
            return;
        }
        for (AnnotationNode node : nodes) {
            annotations.add("@" + Type.getType(node.desc).getClassName());
        }
    }
}
