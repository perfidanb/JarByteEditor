package vn.perfidanb.jarbe.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

public final class SafeClassWriter extends ClassWriter {
    public SafeClassWriter(ClassReader classReader, int flags) {
        super(classReader, flags);
    }

    public SafeClassWriter(int flags) {
        super(flags);
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        try {
            return super.getCommonSuperClass(type1, type2);
        } catch (RuntimeException ignored) {
            return "java/lang/Object";
        }
    }
}
