package vn.perfidanb.jarbe.model;

import java.util.List;

public record ClassSummary(
        String name,
        String superName,
        List<String> interfaces,
        int majorVersion,
        int javaVersion,
        int access,
        List<FieldSummary> fields,
        List<MethodSummary> methods,
        List<String> annotations,
        int instructionCount
) {
}
