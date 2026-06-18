package vn.perfidanb.jarbe.diff;

import vn.perfidanb.jarbe.asm.AsmClassAnalyzer;
import vn.perfidanb.jarbe.model.ClassSummary;
import vn.perfidanb.jarbe.model.DiffReport;
import vn.perfidanb.jarbe.model.EntryType;
import vn.perfidanb.jarbe.model.FieldSummary;
import vn.perfidanb.jarbe.model.JarEntryData;
import vn.perfidanb.jarbe.model.JarProject;
import vn.perfidanb.jarbe.model.MethodSummary;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class DiffEngine {
    private final AsmClassAnalyzer analyzer = new AsmClassAnalyzer();

    public DiffReport diff(JarProject oldProject, JarProject newProject) {
        DiffReport report = new DiffReport();
        Map<String, JarEntryData> oldEntries = oldProject.entries().stream()
                .collect(Collectors.toMap(JarEntryData::path, Function.identity()));
        Map<String, JarEntryData> newEntries = newProject.entries().stream()
                .collect(Collectors.toMap(JarEntryData::path, Function.identity()));

        for (String path : newEntries.keySet()) {
            if (!oldEntries.containsKey(path)) {
                report.addedEntries().add(path);
            }
        }
        for (String path : oldEntries.keySet()) {
            if (!newEntries.containsKey(path)) {
                report.removedEntries().add(path);
            }
        }
        for (String path : oldEntries.keySet()) {
            JarEntryData oldEntry = oldEntries.get(path);
            JarEntryData newEntry = newEntries.get(path);
            if (newEntry == null || Arrays.equals(oldEntry.bytes(), newEntry.bytes())) {
                continue;
            }
            if (oldEntry.type() == EntryType.CLASS && newEntry.type() == EntryType.CLASS) {
                diffClass(path, oldEntry.bytes(), newEntry.bytes(), report);
            } else {
                report.changedResources().add(path);
            }
        }
        return report;
    }

    private void diffClass(String path, byte[] oldBytes, byte[] newBytes, DiffReport report) {
        report.changedClasses().add(path);
        try {
            ClassSummary oldSummary = analyzer.analyze(oldBytes);
            ClassSummary newSummary = analyzer.analyze(newBytes);
            Map<String, MethodSummary> oldMethods = oldSummary.methods().stream()
                    .collect(Collectors.toMap(MethodSummary::key, Function.identity(), (a, b) -> a));
            Map<String, MethodSummary> newMethods = newSummary.methods().stream()
                    .collect(Collectors.toMap(MethodSummary::key, Function.identity(), (a, b) -> a));
            for (MethodSummary method : newSummary.methods()) {
                MethodSummary old = oldMethods.get(method.key());
                if (old == null || old.access() != method.access() || old.instructionCount() != method.instructionCount()) {
                    report.changedMethods().add(path + " " + method.key());
                }
            }
            for (MethodSummary method : oldSummary.methods()) {
                if (!newMethods.containsKey(method.key())) {
                    report.changedMethods().add(path + " removed " + method.key());
                }
            }

            Map<String, FieldSummary> oldFields = oldSummary.fields().stream()
                    .collect(Collectors.toMap(field -> field.name() + ":" + field.descriptor(), Function.identity(), (a, b) -> a));
            Map<String, FieldSummary> newFields = newSummary.fields().stream()
                    .collect(Collectors.toMap(field -> field.name() + ":" + field.descriptor(), Function.identity(), (a, b) -> a));
            for (FieldSummary field : newSummary.fields()) {
                String key = field.name() + ":" + field.descriptor();
                FieldSummary old = oldFields.get(key);
                if (old == null || old.access() != field.access()) {
                    report.changedFields().add(path + " " + key);
                }
            }
            for (String key : oldFields.keySet()) {
                if (!newFields.containsKey(key)) {
                    report.changedFields().add(path + " removed " + key);
                }
            }
        } catch (RuntimeException e) {
            report.changedMethods().add(path + " ASM parse error: " + e.getMessage());
        }
    }
}
