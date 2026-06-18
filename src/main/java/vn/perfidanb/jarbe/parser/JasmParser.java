package vn.perfidanb.jarbe.parser;

import java.util.Arrays;
import java.util.List;

public final class JasmParser {
    public JasmDocument parseSummary(String jasm) {
        JasmDocument document = new JasmDocument();
        Arrays.stream(jasm.replace("\r\n", "\n").split("\n"))
                .map(String::strip)
                .filter(line -> !line.isBlank() && !line.startsWith(";"))
                .forEach(line -> parseLine(document, line));
        return document;
    }

    private static void parseLine(JasmDocument document, String line) {
        if (line.startsWith("CLASS ")) {
            List<String> parts = List.of(line.substring("CLASS ".length()).split("\\s+"));
            document.addClassName(parts.getLast());
        } else if (line.startsWith("METHOD ")) {
            List<String> parts = List.of(line.substring("METHOD ".length()).split("\\s+"));
            if (parts.size() >= 2) {
                document.addMethodKey(parts.get(parts.size() - 2) + parts.getLast());
            }
        } else if (line.startsWith("FIELD ")) {
            List<String> parts = List.of(line.substring("FIELD ".length()).split("\\s+"));
            if (parts.size() >= 2) {
                document.addFieldKey(parts.get(parts.size() - 2) + ":" + parts.getLast());
            }
        }
    }
}
