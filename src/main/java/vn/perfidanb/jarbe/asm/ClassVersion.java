package vn.perfidanb.jarbe.asm;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ClassVersion {
    private static final Map<Integer, Integer> JAVA_TO_MAJOR = new LinkedHashMap<>();

    static {
        for (int java = 8; java <= 25; java++) {
            JAVA_TO_MAJOR.put(java, java + 44);
        }
    }

    private ClassVersion() {
    }

    public static int detectMajor(byte[] classBytes) {
        if (classBytes.length < 8) {
            throw new IllegalArgumentException("Invalid bytecode: class file is too small");
        }
        int magic = ((classBytes[0] & 0xff) << 24)
                | ((classBytes[1] & 0xff) << 16)
                | ((classBytes[2] & 0xff) << 8)
                | (classBytes[3] & 0xff);
        if (magic != 0xCAFEBABE) {
            throw new IllegalArgumentException("Invalid bytecode: missing CAFEBABE header");
        }
        return ((classBytes[6] & 0xff) << 8) | (classBytes[7] & 0xff);
    }

    public static int majorForJava(int javaVersion) {
        Integer major = JAVA_TO_MAJOR.get(javaVersion);
        if (major == null) {
            throw new IllegalArgumentException("Unsupported target Java version: " + javaVersion);
        }
        return major;
    }

    public static int javaForMajor(int majorVersion) {
        return majorVersion - 44;
    }

    public static boolean supportsMajor(int majorVersion) {
        return majorVersion >= 52 && majorVersion <= 69;
    }

    public static String display(int majorVersion) {
        int java = javaForMajor(majorVersion);
        if (supportsMajor(majorVersion)) {
            return "Java " + java + " / class " + majorVersion;
        }
        return "Unknown Java / class " + majorVersion;
    }
}
