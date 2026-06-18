package vn.perfidanb.jarbe.service;

import vn.perfidanb.jarbe.asm.AsmClassAnalyzer;
import vn.perfidanb.jarbe.model.ClassSummary;
import vn.perfidanb.jarbe.model.EntryType;
import vn.perfidanb.jarbe.model.JarEntryData;
import vn.perfidanb.jarbe.model.JarProject;
import vn.perfidanb.jarbe.model.MethodSummary;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CallGraphService {
    private final AsmClassAnalyzer analyzer = new AsmClassAnalyzer();

    public Map<String, List<String>> build(JarProject project) {
        Map<String, List<String>> graph = new LinkedHashMap<>();
        for (JarEntryData entry : project.classEntries()) {
            if (entry.type() != EntryType.CLASS) {
                continue;
            }
            try {
                ClassSummary summary = analyzer.analyze(entry.readBytesOnce());
                for (MethodSummary method : summary.methods()) {
                    graph.put(summary.name() + "." + method.name() + method.descriptor(), method.calls());
                }
            } catch (RuntimeException ignored) {
                graph.put(entry.path(), List.of("ASM parse error"));
            }
        }
        return graph;
    }

    public String toDisplayString(JarProject project) {
        StringBuilder builder = new StringBuilder();
        build(project).forEach((method, calls) -> {
            builder.append(method).append(System.lineSeparator());
            for (String call : calls) {
                builder.append("  -> ").append(call).append(System.lineSeparator());
            }
        });
        return builder.isEmpty() ? "No method calls found" : builder.toString();
    }
}
