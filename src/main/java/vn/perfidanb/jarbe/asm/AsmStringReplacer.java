package vn.perfidanb.jarbe.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

public final class AsmStringReplacer {
    public byte[] replace(byte[] classBytes, String find, String replacement, Integer targetJava) {
        ClassReader reader = new ClassReader(classBytes);
        ClassNode node = new ClassNode();
        reader.accept(node, ClassReader.EXPAND_FRAMES);
        boolean[] changed = {false};

        for (FieldNode field : node.fields) {
            if (field.value instanceof String string && string.contains(find)) {
                field.value = string.replace(find, replacement);
                changed[0] = true;
            }
            changed[0] |= replaceAnnotations(field.visibleAnnotations, find, replacement);
            changed[0] |= replaceAnnotations(field.invisibleAnnotations, find, replacement);
        }
        for (MethodNode method : node.methods) {
            method.instructions.forEach(instruction -> {
                if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof String string && string.contains(find)) {
                    ldc.cst = string.replace(find, replacement);
                    changed[0] = true;
                }
            });
            changed[0] |= replaceAnnotations(method.visibleAnnotations, find, replacement);
            changed[0] |= replaceAnnotations(method.invisibleAnnotations, find, replacement);
        }
        changed[0] |= replaceAnnotations(node.visibleAnnotations, find, replacement);
        changed[0] |= replaceAnnotations(node.invisibleAnnotations, find, replacement);

        if (targetJava != null) {
            node.version = ClassVersion.majorForJava(targetJava);
            changed[0] = true;
        }
        if (!changed[0]) {
            return classBytes;
        }
        SafeClassWriter writer = new SafeClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static boolean replaceAnnotations(List<AnnotationNode> annotations, String find, String replacement) {
        if (annotations == null) {
            return false;
        }
        boolean changed = false;
        for (AnnotationNode annotation : annotations) {
            if (annotation.values == null) {
                continue;
            }
            List<Object> values = new ArrayList<>(annotation.values);
            for (int i = 1; i < values.size(); i += 2) {
                Object value = values.get(i);
                Object updated = replaceAnnotationValue(value, find, replacement);
                if (updated != value) {
                    values.set(i, updated);
                    changed = true;
                }
            }
            annotation.values = values;
        }
        return changed;
    }

    @SuppressWarnings("unchecked")
    private static Object replaceAnnotationValue(Object value, String find, String replacement) {
        if (value instanceof String string && string.contains(find)) {
            return string.replace(find, replacement);
        }
        if (value instanceof AnnotationNode annotation) {
            replaceAnnotations(List.of(annotation), find, replacement);
            return annotation;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>((List<Object>) list);
            boolean changed = false;
            for (int i = 0; i < copy.size(); i++) {
                Object updated = replaceAnnotationValue(copy.get(i), find, replacement);
                if (updated != copy.get(i)) {
                    copy.set(i, updated);
                    changed = true;
                }
            }
            return changed ? copy : value;
        }
        return value;
    }
}
