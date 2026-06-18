package vn.perfidanb.jarbe.model;

public record SearchResult(String type, String path, String owner, String name, String value) {
    public String compact() {
        StringBuilder builder = new StringBuilder(type).append(" ").append(path);
        if (owner != null && !owner.isBlank()) {
            builder.append(" ").append(owner);
        }
        if (name != null && !name.isBlank()) {
            builder.append(" ").append(name);
        }
        if (value != null && !value.isBlank()) {
            builder.append(" -> ").append(value);
        }
        return builder.toString();
    }
}
