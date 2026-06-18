package vn.perfidanb.jarbe.model;

import java.util.ArrayList;
import java.util.List;

public final class ExportManifest {
    private String sourceName;
    private List<ExportedEntry> entries = new ArrayList<>();

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public List<ExportedEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<ExportedEntry> entries) {
        this.entries = entries;
    }

    public static final class ExportedEntry {
        private String path;
        private String type;
        private String file;
        private String jasmFile;
        private String originalSha1;
        private String jasmSha1;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getFile() {
            return file;
        }

        public void setFile(String file) {
            this.file = file;
        }

        public String getJasmFile() {
            return jasmFile;
        }

        public void setJasmFile(String jasmFile) {
            this.jasmFile = jasmFile;
        }

        public String getOriginalSha1() {
            return originalSha1;
        }

        public void setOriginalSha1(String originalSha1) {
            this.originalSha1 = originalSha1;
        }

        public String getJasmSha1() {
            return jasmSha1;
        }

        public void setJasmSha1(String jasmSha1) {
            this.jasmSha1 = jasmSha1;
        }
    }
}
