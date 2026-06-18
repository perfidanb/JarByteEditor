package vn.perfidanb.jarbe.parser;

import java.util.ArrayList;
import java.util.List;

public final class JasmDocument {
    private final List<String> classNames = new ArrayList<>();
    private final List<String> methodKeys = new ArrayList<>();
    private final List<String> fieldKeys = new ArrayList<>();

    public List<String> classNames() {
        return List.copyOf(classNames);
    }

    public List<String> methodKeys() {
        return List.copyOf(methodKeys);
    }

    public List<String> fieldKeys() {
        return List.copyOf(fieldKeys);
    }

    void addClassName(String className) {
        classNames.add(className);
    }

    void addMethodKey(String methodKey) {
        methodKeys.add(methodKey);
    }

    void addFieldKey(String fieldKey) {
        fieldKeys.add(fieldKey);
    }
}
