package vn.perfidanb.jarbe.model;

import java.util.ArrayList;
import java.util.List;

public final class DiffReport {
    private final List<String> addedEntries = new ArrayList<>();
    private final List<String> removedEntries = new ArrayList<>();
    private final List<String> changedResources = new ArrayList<>();
    private final List<String> changedClasses = new ArrayList<>();
    private final List<String> changedMethods = new ArrayList<>();
    private final List<String> changedFields = new ArrayList<>();

    public List<String> addedEntries() {
        return addedEntries;
    }

    public List<String> removedEntries() {
        return removedEntries;
    }

    public List<String> changedResources() {
        return changedResources;
    }

    public List<String> changedClasses() {
        return changedClasses;
    }

    public List<String> changedMethods() {
        return changedMethods;
    }

    public List<String> changedFields() {
        return changedFields;
    }

    public boolean empty() {
        return addedEntries.isEmpty()
                && removedEntries.isEmpty()
                && changedResources.isEmpty()
                && changedClasses.isEmpty()
                && changedMethods.isEmpty()
                && changedFields.isEmpty();
    }

    public String toDisplayString() {
        StringBuilder builder = new StringBuilder();
        append(builder, "Added Entries", addedEntries);
        append(builder, "Removed Entries", removedEntries);
        append(builder, "Changed Classes", changedClasses);
        append(builder, "Changed Methods", changedMethods);
        append(builder, "Changed Fields", changedFields);
        append(builder, "Changed Resources", changedResources);
        if (builder.isEmpty()) {
            return "No differences";
        }
        return builder.toString();
    }

    private static void append(StringBuilder builder, String title, List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        builder.append(title).append(':').append(System.lineSeparator());
        for (String value : values) {
            builder.append("  ").append(value).append(System.lineSeparator());
        }
    }
}
