package vn.perfidanb.jarbe.model;

import java.util.List;

public record MethodSummary(String name, String descriptor, int access, List<String> calls, int instructionCount) {
    public String key() {
        return name + descriptor;
    }
}
