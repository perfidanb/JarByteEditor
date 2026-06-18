package vn.perfidanb.jarbe.asm;

import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class AccessFlagUtil {
    private static final Set<String> KNOWN = Set.of(
            "public", "private", "protected", "static", "final", "abstract",
            "native", "synchronized", "record", "enum", "interface",
            "annotation", "strict", "volatile", "transient", "synthetic"
    );

    private AccessFlagUtil() {
    }

    public static String classAccessToText(int access) {
        return accessToWords(access, true);
    }

    public static String memberAccessToText(int access) {
        return accessToWords(access, false);
    }

    public static String accessToWords(int access, boolean classAccess) {
        List<String> words = new ArrayList<>();
        add(access, Opcodes.ACC_PUBLIC, "public", words);
        add(access, Opcodes.ACC_PRIVATE, "private", words);
        add(access, Opcodes.ACC_PROTECTED, "protected", words);
        add(access, Opcodes.ACC_STATIC, "static", words);
        add(access, Opcodes.ACC_FINAL, "final", words);
        add(access, Opcodes.ACC_ABSTRACT, "abstract", words);
        add(access, Opcodes.ACC_NATIVE, "native", words);
        add(access, Opcodes.ACC_SYNCHRONIZED, "synchronized", words);
        add(access, Opcodes.ACC_STRICT, "strict", words);
        add(access, Opcodes.ACC_VOLATILE, "volatile", words);
        add(access, Opcodes.ACC_TRANSIENT, "transient", words);
        add(access, Opcodes.ACC_SYNTHETIC, "synthetic", words);
        if (classAccess) {
            add(access, Opcodes.ACC_RECORD, "record", words);
            add(access, Opcodes.ACC_ENUM, "enum", words);
            add(access, Opcodes.ACC_INTERFACE, "interface", words);
            add(access, Opcodes.ACC_ANNOTATION, "annotation", words);
        }
        return String.join(" ", words);
    }

    public static boolean isAccessWord(String token) {
        return KNOWN.contains(token);
    }

    public static int parseAccess(List<String> words, int base) {
        int access = base;
        access &= ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED
                | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ABSTRACT
                | Opcodes.ACC_NATIVE | Opcodes.ACC_SYNCHRONIZED | Opcodes.ACC_STRICT
                | Opcodes.ACC_VOLATILE | Opcodes.ACC_TRANSIENT | Opcodes.ACC_RECORD
                | Opcodes.ACC_ENUM | Opcodes.ACC_INTERFACE | Opcodes.ACC_ANNOTATION
                | Opcodes.ACC_SYNTHETIC);
        for (String word : words) {
            access |= switch (word) {
                case "public" -> Opcodes.ACC_PUBLIC;
                case "private" -> Opcodes.ACC_PRIVATE;
                case "protected" -> Opcodes.ACC_PROTECTED;
                case "static" -> Opcodes.ACC_STATIC;
                case "final" -> Opcodes.ACC_FINAL;
                case "abstract" -> Opcodes.ACC_ABSTRACT;
                case "native" -> Opcodes.ACC_NATIVE;
                case "synchronized" -> Opcodes.ACC_SYNCHRONIZED;
                case "strict" -> Opcodes.ACC_STRICT;
                case "volatile" -> Opcodes.ACC_VOLATILE;
                case "transient" -> Opcodes.ACC_TRANSIENT;
                case "record" -> Opcodes.ACC_RECORD;
                case "enum" -> Opcodes.ACC_ENUM;
                case "interface" -> Opcodes.ACC_INTERFACE;
                case "annotation" -> Opcodes.ACC_ANNOTATION;
                case "synthetic" -> Opcodes.ACC_SYNTHETIC;
                default -> 0;
            };
        }
        return access;
    }

    private static void add(int access, int flag, String word, List<String> words) {
        if ((access & flag) != 0) {
            words.add(word);
        }
    }
}
