package vn.perfidanb.jarbe.search;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Printer;
import vn.perfidanb.jarbe.model.EntryType;
import vn.perfidanb.jarbe.model.JarEntryData;
import vn.perfidanb.jarbe.model.JarProject;
import vn.perfidanb.jarbe.model.SearchResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SearchEngine {
    public List<SearchResult> search(JarProject project, String query, SearchType type) {
        String needle = query.toLowerCase(Locale.ROOT);
        List<SearchResult> results = new ArrayList<>();
        for (JarEntryData entry : project.entries()) {
            if (entry.type() == EntryType.CLASS) {
                searchClass(entry, needle, type, results);
            } else if ((type == SearchType.ALL || type == SearchType.STRING) && entry.type() == EntryType.TEXT_RESOURCE) {
                String text = new String(entry.readBytesOnce(), StandardCharsets.UTF_8);
                String lower = text.toLowerCase(Locale.ROOT);
                int index = lower.indexOf(needle);
                while (index >= 0) {
                    int line = 1;
                    for (int i = 0; i < index; i++) {
                        if (text.charAt(i) == '\n') line++;
                    }
                    results.add(new SearchResult("Resource", entry.path(), null, null, excerptText(text, index, query), line));
                    index = lower.indexOf(needle, index + 1);
                }
            }
        }
        return results;
    }

    private static void searchClass(JarEntryData entry, String needle, SearchType type, List<SearchResult> results) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(entry.readBytesOnce()).accept(node, ClassReader.EXPAND_FRAMES);
            if (matches(type, SearchType.CLASS) && contains(node.name, needle)) {
                results.add(new SearchResult("Class", entry.path(), node.name, null, node.name, 0));
            }
            for (FieldNode field : node.fields) {
                if (matches(type, SearchType.FIELD) && (contains(field.name, needle) || contains(field.desc, needle))) {
                    results.add(new SearchResult("Field", entry.path(), node.name, field.name, field.desc, 0));
                }
                if (matches(type, SearchType.STRING) && field.value instanceof String string && contains(string, needle)) {
                    results.add(new SearchResult("String", entry.path(), node.name, field.name, string, 0));
                }
                searchAnnotations(entry.path(), node.name, field.name, field.visibleAnnotations, needle, type, results);
                searchAnnotations(entry.path(), node.name, field.name, field.invisibleAnnotations, needle, type, results);
            }
            for (MethodNode method : node.methods) {
                if (matches(type, SearchType.METHOD) && (contains(method.name, needle) || contains(method.desc, needle))) {
                    results.add(new SearchResult("Method", entry.path(), node.name, method.name, method.desc, 0));
                }
                searchAnnotations(entry.path(), node.name, method.name, method.visibleAnnotations, needle, type, results);
                searchAnnotations(entry.path(), node.name, method.name, method.invisibleAnnotations, needle, type, results);
                for (AbstractInsnNode instruction : method.instructions) {
                    if (matches(type, SearchType.STRING) && instruction instanceof LdcInsnNode ldc && ldc.cst instanceof String string && contains(string, needle)) {
                        results.add(new SearchResult("String", entry.path(), node.name, method.name, string, 0));
                    }
                    if (matches(type, SearchType.OPCODE) && instruction.getOpcode() >= 0) {
                        String opcode = Printer.OPCODES[instruction.getOpcode()];
                        if (contains(opcode, needle)) {
                            results.add(new SearchResult("Opcode", entry.path(), node.name, method.name, opcode, 0));
                        }
                    }
                }
            }
            searchAnnotations(entry.path(), node.name, node.name, node.visibleAnnotations, needle, type, results);
            searchAnnotations(entry.path(), node.name, node.name, node.invisibleAnnotations, needle, type, results);
        } catch (RuntimeException e) {
            if (matches(type, SearchType.CLASS) && contains(entry.path(), needle)) {
                results.add(new SearchResult("Class", entry.path(), null, null, "ASM parse error: " + e.getMessage(), 0));
            }
        }
    }

    private static void searchAnnotations(String path, String owner, String name, List<AnnotationNode> annotations,
                                          String needle, SearchType type, List<SearchResult> results) {
        if (!matches(type, SearchType.ANNOTATION) || annotations == null) {
            return;
        }
        for (AnnotationNode annotation : annotations) {
            if (contains(annotation.desc, needle)) {
                results.add(new SearchResult("Annotation", path, owner, name, annotation.desc, 0));
            }
            if (annotation.values != null) {
                for (Object value : annotation.values) {
                    if (contains(String.valueOf(value), needle)) {
                        results.add(new SearchResult("Annotation", path, owner, name, String.valueOf(value), 0));
                    }
                }
            }
        }
    }

    private static boolean matches(SearchType selected, SearchType candidate) {
        return selected == SearchType.ALL || selected == candidate;
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String excerpt(String text, String query) {
        String lower = text.toLowerCase(Locale.ROOT);
        int index = lower.indexOf(query.toLowerCase(Locale.ROOT));
        return excerptText(text, index, query);
    }

    private static String excerptText(String text, int index, String query) {
        if (index < 0) {
            return text.substring(0, Math.min(120, text.length()));
        }
        int start = Math.max(0, index - 40);
        int end = Math.min(text.length(), index + query.length() + 80);
        return text.substring(start, end).replace('\n', ' ').replace('\r', ' ');
    }
}
