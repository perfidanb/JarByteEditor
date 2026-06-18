package vn.perfidanb.jarbe.service;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;
import vn.perfidanb.jarbe.asm.SafeClassWriter;
import vn.perfidanb.jarbe.model.EntryType;
import vn.perfidanb.jarbe.model.JarEntryData;
import vn.perfidanb.jarbe.model.JarProject;
import vn.perfidanb.jarbe.model.TranslationCandidate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TranslationEngine {
    private static final Pattern QUOTED_STRING = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern XML_TEXT = Pattern.compile(">([^<>]+)<");
    private static final Pattern WORD = Pattern.compile("\\p{L}{2,}");

    private final TranslationClient client;

    public TranslationEngine(TranslationClient client) {
        this.client = client;
    }

    public List<TranslationCandidate> preview(JarProject project, String sourceLanguage, String targetLanguage)
            throws IOException, InterruptedException {
        return preview(project, sourceLanguage, targetLanguage, TranslationProgress.noop());
    }

    public List<TranslationCandidate> preview(JarProject project, String sourceLanguage, String targetLanguage,
                                              TranslationProgress progress)
            throws IOException, InterruptedException {
        List<ScannedText> scanned = scan(project, progress);
        progress.log("Found " + scanned.size() + " translatable text candidates.");
        progress.update(0, Math.max(scanned.size(), 1));
        List<String> texts = scanned.stream().map(ScannedText::text).toList();
        List<String> translated = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Translation cancelled");
            }
            String text = texts.get(i);
            progress.log("Translating " + (i + 1) + "/" + texts.size() + ": " + abbreviate(text, 90));
            translated.add(client.translate(List.of(text), sourceLanguage, targetLanguage).getFirst());
            progress.update(i + 1, Math.max(texts.size(), 1));
        }
        progress.log("Building preview list...");
        List<TranslationCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < scanned.size(); i++) {
            ScannedText text = scanned.get(i);
            String translatedText = translated.get(i);
            if (!translatedText.isBlank() && !translatedText.equals(text.text())) {
                candidates.add(new TranslationCandidate(text.path(), text.location(), text.entryType(), text.text(), translatedText));
            }
        }
        progress.log("Preview ready: " + candidates.size() + " changed strings.");
        return candidates;
    }

    public int apply(JarProject project, List<TranslationCandidate> selected) {
        Map<String, Map<String, String>> byPath = new LinkedHashMap<>();
        for (TranslationCandidate candidate : selected) {
            byPath.computeIfAbsent(candidate.path(), ignored -> new LinkedHashMap<>())
                    .put(candidate.originalText(), candidate.translatedText());
        }

        int changed = 0;
        for (JarEntryData entry : project.entries()) {
            Map<String, String> replacements = byPath.get(entry.path());
            if (replacements == null || replacements.isEmpty()) {
                continue;
            }
            if (entry.type() == EntryType.CLASS) {
                byte[] before = entry.bytes();
                byte[] updated = applyClass(before, replacements);
                if (!java.util.Arrays.equals(updated, before)) {
                    entry.updateBytes(updated);
                    changed++;
                }
            } else if (entry.type() == EntryType.TEXT_RESOURCE) {
                String text = new String(entry.bytes(), StandardCharsets.UTF_8);
                String updated = applyText(text, replacements);
                if (!updated.equals(text)) {
                    entry.updateBytes(updated.getBytes(StandardCharsets.UTF_8));
                    changed++;
                }
            }
        }
        return changed;
    }

    private static List<ScannedText> scan(JarProject project, TranslationProgress progress) throws InterruptedException {
        progress.log("Scanning project entries one by one...");
        List<ScannedText> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<JarEntryData> entries = List.copyOf(project.entries());
        for (int i = 0; i < entries.size(); i++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Translation cancelled");
            }
            JarEntryData entry = entries.get(i);
            try {
                boolean logEntry = shouldLogEntry(i, entries.size());
                if (entry.type() == EntryType.TEXT_RESOURCE) {
                    if (logEntry) {
                        progress.log("Scanning resource " + (i + 1) + "/" + entries.size() + ": " + entry.path());
                    }
                    scanResource(entry, result, seen);
                } else if (entry.type() == EntryType.CLASS) {
                    if (logEntry) {
                        progress.log("Scanning class " + (i + 1) + "/" + entries.size() + ": " + entry.path());
                    }
                    scanClass(entry, result, seen);
                }
            } catch (RuntimeException ignored) {
                // Keep translation moving when one entry cannot be read or parsed.
            }
            progress.update(i + 1, Math.max(entries.size(), 1));
        }
        return result;
    }

    private static boolean shouldLogEntry(int index, int total) {
        return index == 0 || index == total - 1 || (index + 1) % 25 == 0;
    }

    private static void scanResource(JarEntryData entry, List<ScannedText> result, Set<String> seen) {
        String text = new String(entry.readBytesOnce(), StandardCharsets.UTF_8);
        String lower = entry.path().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".json")) {
            Matcher matcher = QUOTED_STRING.matcher(text);
            while (matcher.find()) {
                int next = nextNonWhitespace(text, matcher.end());
                if (next < text.length() && text.charAt(next) == ':') {
                    continue;
                }
                add(entry, "json string", unescapeJson(matcher.group(1)), result, seen);
            }
            return;
        }
        if (lower.endsWith(".xml") || lower.endsWith(".html")) {
            Matcher matcher = XML_TEXT.matcher(text);
            while (matcher.find()) {
                add(entry, "xml text", matcher.group(1).strip(), result, seen);
            }
            return;
        }
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String candidate = extractLineValue(lines[i], lower);
            add(entry, "line " + (i + 1), candidate, result, seen);
        }
    }

    private static void scanClass(JarEntryData entry, List<ScannedText> result, Set<String> seen) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(entry.readBytesOnce()).accept(node, ClassReader.EXPAND_FRAMES);
            for (FieldNode field : node.fields) {
                if (field.value instanceof String string) {
                    add(entry, "field " + field.name, string, result, seen);
                }
            }
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof String string) {
                        add(entry, "method " + method.name, string, result, seen);
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Invalid classes are still editable elsewhere; translation simply skips them.
        }
    }

    private static byte[] applyClass(byte[] classBytes, Map<String, String> replacements) {
        ClassReader reader = new ClassReader(classBytes);
        ClassNode node = new ClassNode();
        reader.accept(node, ClassReader.EXPAND_FRAMES);
        boolean changed = false;
        for (FieldNode field : node.fields) {
            if (field.value instanceof String string && replacements.containsKey(string)) {
                field.value = replacements.get(string);
                changed = true;
            }
        }
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof LdcInsnNode ldc && ldc.cst instanceof String string && replacements.containsKey(string)) {
                    ldc.cst = replacements.get(string);
                    changed = true;
                }
            }
        }
        if (!changed) {
            return classBytes;
        }
        SafeClassWriter writer = new SafeClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static String applyText(String text, Map<String, String> replacements) {
        String updated = text;
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            updated = updated.replace(replacement.getKey(), replacement.getValue());
        }
        return updated;
    }

    private static void add(JarEntryData entry, String location, String rawText, List<ScannedText> result, Set<String> seen) {
        String text = cleanCandidate(rawText);
        if (!looksTranslatable(text)) {
            return;
        }
        String key = entry.path() + '\n' + text;
        if (seen.add(key)) {
            result.add(new ScannedText(entry.path(), location, entry.type(), text));
        }
    }

    private static String extractLineValue(String line, String lowerPath) {
        String stripped = line.strip();
        if (stripped.isEmpty() || stripped.startsWith("#") || stripped.startsWith("//")) {
            return "";
        }
        int separator = -1;
        if (lowerPath.endsWith(".properties")) {
            separator = firstSeparator(stripped, '=', ':');
        } else if (lowerPath.endsWith(".yml") || lowerPath.endsWith(".yaml")) {
            separator = firstSeparator(stripped, ':');
        }
        if (separator >= 0 && separator + 1 < stripped.length()) {
            return stripped.substring(separator + 1).strip();
        }
        return stripped;
    }

    private static int firstSeparator(String value, char... separators) {
        int best = -1;
        for (char separator : separators) {
            int index = value.indexOf(separator);
            if (index >= 0 && (best < 0 || index < best)) {
                best = index;
            }
        }
        return best;
    }

    private static String cleanCandidate(String value) {
        String text = value == null ? "" : value.strip();
        if (text.length() >= 2
                && ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'")))) {
            text = text.substring(1, text.length() - 1).strip();
        }
        return text;
    }

    private static boolean looksTranslatable(String text) {
        if (text.length() < 2 || text.length() > 500) {
            return false;
        }
        if (text.startsWith("http://") || text.startsWith("https://") || text.contains("/") && !text.contains(" ")) {
            return false;
        }
        if (text.matches("[A-Z0-9_./:$#{}\\-]+")) {
            return false;
        }
        return WORD.matcher(text).find();
    }

    private static int nextNonWhitespace(String text, int start) {
        for (int i = start; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return text.length();
    }

    private static String unescapeJson(String value) {
        return value.replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    private static String abbreviate(String text, int maxLength) {
        String oneLine = text.replace('\n', ' ').replace('\r', ' ').strip();
        if (oneLine.length() <= maxLength) {
            return oneLine;
        }
        return oneLine.substring(0, maxLength - 3) + "...";
    }

    public interface TranslationProgress {
        void log(String message);

        void update(int completed, int total);

        static TranslationProgress noop() {
            return new TranslationProgress() {
                @Override
                public void log(String message) {
                }

                @Override
                public void update(int completed, int total) {
                }
            };
        }
    }

    private record ScannedText(String path, String location, EntryType entryType, String text) {
    }
}
