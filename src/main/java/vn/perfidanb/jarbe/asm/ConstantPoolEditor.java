package vn.perfidanb.jarbe.asm;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class ConstantPoolEditor {
    public byte[] replaceUtf8(byte[] classBytes, int constantPoolIndex, String newValue) {
        if (constantPoolIndex <= 0) {
            throw new IllegalArgumentException("Constant pool index starts at 1");
        }
        if (classBytes.length < 10) {
            throw new IllegalArgumentException("Invalid bytecode: class file is too small");
        }
        int count = u2(classBytes, 8);
        int offset = 10;
        for (int index = 1; index < count; index++) {
            int entryOffset = offset;
            int tag = u1(classBytes, offset++);
            switch (tag) {
                case 1 -> {
                    int length = u2(classBytes, offset);
                    offset += 2;
                    if (index == constantPoolIndex) {
                        byte[] replacement = newValue.getBytes(StandardCharsets.UTF_8);
                        ByteArrayOutputStream out = new ByteArrayOutputStream(classBytes.length + replacement.length - length);
                        out.writeBytes(Arrays.copyOfRange(classBytes, 0, entryOffset));
                        out.write(1);
                        out.write((replacement.length >>> 8) & 0xff);
                        out.write(replacement.length & 0xff);
                        out.writeBytes(replacement);
                        out.writeBytes(Arrays.copyOfRange(classBytes, offset + length, classBytes.length));
                        return out.toByteArray();
                    }
                    offset += length;
                }
                case 3, 4 -> offset += 4;
                case 5, 6 -> {
                    offset += 8;
                    index++;
                }
                case 7, 8, 16, 19, 20 -> offset += 2;
                case 9, 10, 11, 12, 17, 18 -> offset += 4;
                case 15 -> offset += 3;
                default -> throw new IllegalArgumentException("Invalid bytecode: unknown constant pool tag " + tag);
            }
        }
        throw new IllegalArgumentException("Constant pool entry #" + constantPoolIndex + " is not a Utf8 entry");
    }

    private static int u1(byte[] bytes, int offset) {
        return bytes[offset] & 0xff;
    }

    private static int u2(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }
}
