package vn.perfidanb.jarbe.model;

public record ProjectStats(
        int classes,
        int methods,
        int fields,
        int instructions,
        int resources
) {
    public String toDisplayString() {
        return "Classes: " + classes + System.lineSeparator()
                + "Methods: " + methods + System.lineSeparator()
                + "Fields: " + fields + System.lineSeparator()
                + "Instructions: " + instructions + System.lineSeparator()
                + "Resources: " + resources;
    }
}
