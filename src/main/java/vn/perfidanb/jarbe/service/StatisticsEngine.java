package vn.perfidanb.jarbe.service;

import vn.perfidanb.jarbe.asm.AsmClassAnalyzer;
import vn.perfidanb.jarbe.model.ClassSummary;
import vn.perfidanb.jarbe.model.EntryType;
import vn.perfidanb.jarbe.model.JarEntryData;
import vn.perfidanb.jarbe.model.JarProject;
import vn.perfidanb.jarbe.model.ProjectStats;

public final class StatisticsEngine {
    private final AsmClassAnalyzer analyzer = new AsmClassAnalyzer();

    public ProjectStats calculate(JarProject project) {
        int classes = 0;
        int methods = 0;
        int fields = 0;
        int instructions = 0;
        int resources = 0;
        for (JarEntryData entry : project.entries()) {
            if (entry.type() == EntryType.CLASS) {
                classes++;
                try {
                    ClassSummary summary = analyzer.analyze(entry.bytes());
                    methods += summary.methods().size();
                    fields += summary.fields().size();
                    instructions += summary.instructionCount();
                } catch (RuntimeException ignored) {
                    // Keep statistics useful even when one class is damaged.
                }
            } else if (entry.type() == EntryType.TEXT_RESOURCE || entry.type() == EntryType.BINARY_RESOURCE) {
                resources++;
            }
        }
        return new ProjectStats(classes, methods, fields, instructions, resources);
    }
}
