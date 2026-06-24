package vn.perfidanb.jarbe.service;

import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.OutputSinkFactory;
import vn.perfidanb.jarbe.model.JarEntryData;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JavaDecompilerService {

    public String decompile(JarEntryData entry) {
        if (!entry.path().endsWith(".class")) {
            throw new IllegalArgumentException("Cannot decompile non-class entry: " + entry.path());
        }

        StringBuilder output = new StringBuilder();
        
        OutputSinkFactory mySink = new OutputSinkFactory() {
            @Override
            public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> available) {
                return Collections.singletonList(SinkClass.STRING);
            }

            @Override
            public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
                if (sinkType == SinkType.JAVA && sinkClass == SinkClass.STRING) {
                    return x -> output.append(x);
                }
                return ignore -> {};
            }
        };

        org.benf.cfr.reader.api.ClassFileSource classFileSource = new org.benf.cfr.reader.api.ClassFileSource() {
            @Override
            public void informAnalysisRelativePathDetail(String s, String s1) {}

            @Override
            public Collection<String> addJar(String s) {
                return Collections.emptyList();
            }

            @Override
            public String getPossiblyRenamedPath(String s) {
                return s;
            }

            @Override
            public org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair<byte[], String> getClassFileContent(String path) throws java.io.IOException {
                if (path.equals(entry.path())) {
                    return new org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair<>(entry.bytes(), entry.path());
                }
                return null;
            }
        };

        Map<String, String> options = new HashMap<>();
        // You can add CFR options here if needed, e.g., options.put("hideutf", "true");

        CfrDriver driver = new CfrDriver.Builder()
                .withOutputSink(mySink)
                .withClassFileSource(classFileSource)
                .withOptions(options)
                .build();

        driver.analyse(Collections.singletonList(entry.path()));

        return output.toString();
    }
}
