package vn.perfidanb.jarbe.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.util.Printer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SimpleJasmAssembler {
    private static final Map<String, Integer> OPCODES = new HashMap<>();

    static {
        for (int i = 0; i < Printer.OPCODES.length; i++) {
            String name = Printer.OPCODES[i];
            if (name != null) {
                OPCODES.put(name, i);
            }
        }
    }

    public byte[] assemble(String jasm, byte[] originalBytes, Integer targetJava) {
        ClassReader reader = new ClassReader(originalBytes);
        ClassNode node = new ClassNode();
        reader.accept(node, ClassReader.EXPAND_FRAMES);

        List<String> lines = Arrays.stream(jasm.replace("\r\n", "\n").split("\n", -1))
                .map(String::strip)
                .toList();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank() || line.startsWith(";")) {
                continue;
            }
            if (line.startsWith("VERSION ")) {
                int parsedVersion = Integer.parseInt(line.substring("VERSION ".length()).strip());
                node.version = targetJava == null ? parsedVersion : ClassVersion.majorForJava(targetJava);
            } else if (line.startsWith("CLASS ")) {
                parseClassLine(node, line.substring("CLASS ".length()).strip());
            } else if (line.startsWith("SUPER ")) {
                node.superName = line.substring("SUPER ".length()).strip();
            } else if (line.startsWith("INTERFACE ")) {
                String interfaceName = line.substring("INTERFACE ".length()).strip();
                if (!node.interfaces.contains(interfaceName)) {
                    node.interfaces.add(interfaceName);
                }
            } else if (line.startsWith("FIELD ")) {
                parseFieldLine(node, line.substring("FIELD ".length()).strip());
            } else if (line.startsWith("METHOD ")) {
                MethodBlock block = readMethodBlock(lines, index);
                parseMethodBlock(node, block);
                index = block.endLineIndex();
            }
        }

        if (targetJava != null) {
            node.version = ClassVersion.majorForJava(targetJava);
        }
        SafeClassWriter writer = new SafeClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void parseClassLine(ClassNode node, String body) {
        List<String> tokens = splitArguments(body);
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("Invalid CLASS line");
        }
        String name = tokens.getLast();
        node.name = name;
        node.access = AccessFlagUtil.parseAccess(tokens.subList(0, tokens.size() - 1), node.access);
    }

    private static void parseFieldLine(ClassNode node, String body) {
        int equals = body.indexOf(" = ");
        String left = equals < 0 ? body : body.substring(0, equals);
        Object value = equals < 0 ? null : parseConstant(body.substring(equals + 3).strip());
        List<String> tokens = splitArguments(left);
        if (tokens.size() < 2) {
            throw new IllegalArgumentException("Invalid FIELD line: " + body);
        }
        String name = tokens.get(tokens.size() - 2);
        String desc = tokens.get(tokens.size() - 1);
        int access = AccessFlagUtil.parseAccess(tokens.subList(0, tokens.size() - 2), 0);
        FieldNode existing = null;
        for (FieldNode field : node.fields) {
            if (field.name.equals(name) && field.desc.equals(desc)) {
                existing = field;
                break;
            }
        }
        if (existing == null) {
            node.fields.add(new FieldNode(access, name, desc, null, value));
        } else {
            existing.access = access;
            existing.value = value;
        }
    }

    private static MethodBlock readMethodBlock(List<String> lines, int start) {
        String header = lines.get(start).substring("METHOD ".length()).strip();
        List<String> body = new ArrayList<>();
        for (int index = start + 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.equals("END")) {
                return new MethodBlock(header, body, index);
            }
            body.add(line);
        }
        throw new IllegalArgumentException("METHOD without END: " + header);
    }

    private static void parseMethodBlock(ClassNode node, MethodBlock block) {
        List<String> tokens = splitArguments(block.header());
        if (tokens.size() < 2) {
            throw new IllegalArgumentException("Invalid METHOD line: " + block.header());
        }
        String name = tokens.get(tokens.size() - 2);
        String desc = tokens.get(tokens.size() - 1);
        int access = AccessFlagUtil.parseAccess(tokens.subList(0, tokens.size() - 2), 0);
        MethodNode method = null;
        for (MethodNode candidate : node.methods) {
            if (candidate.name.equals(name) && candidate.desc.equals(desc)) {
                method = candidate;
                break;
            }
        }
        if (method == null) {
            method = new MethodNode(access, name, desc, null, null);
            node.methods.add(method);
        } else {
            method.access = access;
        }
        method.instructions = parseInstructions(block.body());
        if (method.tryCatchBlocks != null) {
            method.tryCatchBlocks.clear();
        }
        if (method.localVariables != null) {
            method.localVariables.clear();
        }
        method.maxLocals = 0;
        method.maxStack = 0;
    }

    private static InsnList parseInstructions(List<String> lines) {
        InsnList instructions = new InsnList();
        Map<String, LabelNode> labels = new HashMap<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isBlank() || line.startsWith(";")) {
                continue;
            }
            List<String> args = splitArguments(line);
            if (args.isEmpty()) {
                continue;
            }
            String op = args.getFirst().toUpperCase(Locale.ROOT);
            switch (op) {
                case "LABEL" -> instructions.add(label(labels, requireArg(args, 1, line)));
                case "LINE" -> {
                    int lineNumber = Integer.parseInt(requireArg(args, 1, line));
                    LabelNode start = label(labels, requireArg(args, 2, line));
                    instructions.add(new LineNumberNode(lineNumber, start));
                }
                default -> instructions.add(parseOpcodeInstruction(op, args, labels, line));
            }
        }
        return instructions;
    }

    private static AbstractInsnNode parseOpcodeInstruction(String op, List<String> args, Map<String, LabelNode> labels, String line) {
        Integer opcode = OPCODES.get(op);
        if (opcode == null) {
            throw new IllegalArgumentException("Unsupported instruction: " + line);
        }
        if (isVarOpcode(opcode)) {
            return new VarInsnNode(opcode, Integer.parseInt(requireArg(args, 1, line)));
        }
        if (isIntOpcode(opcode)) {
            return new IntInsnNode(opcode, Integer.parseInt(requireArg(args, 1, line)));
        }
        if (isFieldOpcode(opcode)) {
            return new FieldInsnNode(opcode, requireArg(args, 1, line), requireArg(args, 2, line), requireArg(args, 3, line));
        }
        if (isMethodOpcode(opcode)) {
            boolean itf = opcode == Opcodes.INVOKEINTERFACE
                    || (args.size() > 4 && Boolean.parseBoolean(args.get(4)));
            return new MethodInsnNode(opcode, requireArg(args, 1, line), requireArg(args, 2, line), requireArg(args, 3, line), itf);
        }
        if (isTypeOpcode(opcode)) {
            return new TypeInsnNode(opcode, requireArg(args, 1, line));
        }
        if (isJumpOpcode(opcode)) {
            return new JumpInsnNode(opcode, label(labels, requireArg(args, 1, line)));
        }
        if (opcode == Opcodes.LDC) {
            String value = line.substring(line.indexOf(' ') + 1).strip();
            return new LdcInsnNode(parseConstant(value));
        }
        if (opcode == Opcodes.IINC) {
            return new IincInsnNode(Integer.parseInt(requireArg(args, 1, line)), Integer.parseInt(requireArg(args, 2, line)));
        }
        if (opcode == Opcodes.MULTIANEWARRAY) {
            return new MultiANewArrayInsnNode(requireArg(args, 1, line), Integer.parseInt(requireArg(args, 2, line)));
        }
        return new InsnNode(opcode);
    }

    private static Object parseConstant(String raw) {
        String value = raw.strip();
        if (value.equals("null")) {
            return null;
        }
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return unescape(value.substring(1, value.length() - 1));
        }
        if (value.startsWith("type:")) {
            return Type.getType(value.substring("type:".length()));
        }
        if (value.endsWith("L")) {
            return Long.parseLong(value.substring(0, value.length() - 1));
        }
        if (value.endsWith("F")) {
            return Float.parseFloat(value.substring(0, value.length() - 1));
        }
        if (value.endsWith("D")) {
            return Double.parseDouble(value.substring(0, value.length() - 1));
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    private static List<String> splitArguments(String line) {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean escaping = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaping) {
                current.append(c);
                escaping = false;
                continue;
            }
            if (c == '\\' && quoted) {
                current.append(c);
                escaping = true;
                continue;
            }
            if (c == '"') {
                current.append(c);
                quoted = !quoted;
                continue;
            }
            if (Character.isWhitespace(c) && !quoted) {
                if (!current.isEmpty()) {
                    args.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }
        if (!current.isEmpty()) {
            args.add(current.toString());
        }
        return args;
    }

    private static String unescape(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        boolean escaping = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!escaping && c == '\\') {
                escaping = true;
                continue;
            }
            if (escaping) {
                builder.append(switch (c) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case '"' -> '"';
                    case '\\' -> '\\';
                    default -> c;
                });
                escaping = false;
            } else {
                builder.append(c);
            }
        }
        if (escaping) {
            builder.append('\\');
        }
        return builder.toString();
    }

    private static String requireArg(List<String> args, int index, String line) {
        if (args.size() <= index) {
            throw new IllegalArgumentException("Missing argument in instruction: " + line);
        }
        return args.get(index);
    }

    private static LabelNode label(Map<String, LabelNode> labels, String name) {
        return labels.computeIfAbsent(name, ignored -> new LabelNode());
    }

    private static boolean isVarOpcode(int opcode) {
        return opcode >= Opcodes.ILOAD && opcode <= Opcodes.ALOAD
                || opcode >= Opcodes.ISTORE && opcode <= Opcodes.ASTORE
                || opcode == Opcodes.RET;
    }

    private static boolean isIntOpcode(int opcode) {
        return opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH || opcode == Opcodes.NEWARRAY;
    }

    private static boolean isFieldOpcode(int opcode) {
        return opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC
                || opcode == Opcodes.GETFIELD || opcode == Opcodes.PUTFIELD;
    }

    private static boolean isMethodOpcode(int opcode) {
        return opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKESPECIAL
                || opcode == Opcodes.INVOKESTATIC || opcode == Opcodes.INVOKEINTERFACE;
    }

    private static boolean isTypeOpcode(int opcode) {
        return opcode == Opcodes.NEW || opcode == Opcodes.ANEWARRAY
                || opcode == Opcodes.CHECKCAST || opcode == Opcodes.INSTANCEOF;
    }

    private static boolean isJumpOpcode(int opcode) {
        return opcode >= Opcodes.IFEQ && opcode <= Opcodes.JSR
                || opcode == Opcodes.IFNULL || opcode == Opcodes.IFNONNULL;
    }

    private record MethodBlock(String header, List<String> body, int endLineIndex) {
    }
}
