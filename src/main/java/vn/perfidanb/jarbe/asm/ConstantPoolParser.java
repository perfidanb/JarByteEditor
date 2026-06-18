package vn.perfidanb.jarbe.asm;

import vn.perfidanb.jarbe.model.ConstantPoolEntry;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class ConstantPoolParser {
    public List<ConstantPoolEntry> parse(byte[] classBytes) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(classBytes))) {
            if (in.readInt() != 0xCAFEBABE) {
                throw new IllegalArgumentException("Invalid bytecode: missing CAFEBABE header");
            }
            in.readUnsignedShort();
            in.readUnsignedShort();
            int count = in.readUnsignedShort();
            List<ConstantPoolEntry> entries = new ArrayList<>();
            for (int index = 1; index < count; index++) {
                int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1 -> {
                        int length = in.readUnsignedShort();
                        byte[] utf = in.readNBytes(length);
                        entries.add(new ConstantPoolEntry(index, tag, "Utf8", new String(utf, StandardCharsets.UTF_8)));
                    }
                    case 3 -> {
                        entries.add(new ConstantPoolEntry(index, tag, "Integer", String.valueOf(in.readInt())));
                    }
                    case 4 -> {
                        entries.add(new ConstantPoolEntry(index, tag, "Float", String.valueOf(in.readFloat())));
                    }
                    case 5 -> {
                        entries.add(new ConstantPoolEntry(index, tag, "Long", String.valueOf(in.readLong())));
                        index++;
                    }
                    case 6 -> {
                        entries.add(new ConstantPoolEntry(index, tag, "Double", String.valueOf(in.readDouble())));
                        index++;
                    }
                    case 7 -> entries.add(new ConstantPoolEntry(index, tag, "Class", "#" + in.readUnsignedShort()));
                    case 8 -> entries.add(new ConstantPoolEntry(index, tag, "String", "#" + in.readUnsignedShort()));
                    case 9 -> entries.add(new ConstantPoolEntry(index, tag, "Fieldref", "#" + in.readUnsignedShort() + ".#" + in.readUnsignedShort()));
                    case 10 -> entries.add(new ConstantPoolEntry(index, tag, "Methodref", "#" + in.readUnsignedShort() + ".#" + in.readUnsignedShort()));
                    case 11 -> entries.add(new ConstantPoolEntry(index, tag, "InterfaceMethodref", "#" + in.readUnsignedShort() + ".#" + in.readUnsignedShort()));
                    case 12 -> entries.add(new ConstantPoolEntry(index, tag, "NameAndType", "#" + in.readUnsignedShort() + ":#" + in.readUnsignedShort()));
                    case 15 -> entries.add(new ConstantPoolEntry(index, tag, "MethodHandle", in.readUnsignedByte() + ":#" + in.readUnsignedShort()));
                    case 16 -> entries.add(new ConstantPoolEntry(index, tag, "MethodType", "#" + in.readUnsignedShort()));
                    case 17 -> entries.add(new ConstantPoolEntry(index, tag, "Dynamic", "#" + in.readUnsignedShort() + ":#" + in.readUnsignedShort()));
                    case 18 -> entries.add(new ConstantPoolEntry(index, tag, "InvokeDynamic", "#" + in.readUnsignedShort() + ":#" + in.readUnsignedShort()));
                    case 19 -> entries.add(new ConstantPoolEntry(index, tag, "Module", "#" + in.readUnsignedShort()));
                    case 20 -> entries.add(new ConstantPoolEntry(index, tag, "Package", "#" + in.readUnsignedShort()));
                    default -> throw new IllegalArgumentException("Invalid bytecode: unknown constant pool tag " + tag);
                }
            }
            return entries;
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid bytecode: failed to parse constant pool", e);
        }
    }
}
