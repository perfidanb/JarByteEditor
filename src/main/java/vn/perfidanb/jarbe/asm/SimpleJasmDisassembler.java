package vn.perfidanb.jarbe.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.util.Printer;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public final class SimpleJasmDisassembler {
    public String disassemble(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ClassNode node = new ClassNode();
        reader.accept(node, ClassReader.EXPAND_FRAMES);

        StringBuilder out = new StringBuilder();
        out.append("VERSION ").append(node.version).append(System.lineSeparator());
        out.append("CLASS ").append(AccessFlagUtil.classAccessToText(node.access));
        if (!AccessFlagUtil.classAccessToText(node.access).isBlank()) {
            out.append(' ');
        }
        out.append(node.name).append(System.lineSeparator());
        if (node.superName != null) {
            out.append("SUPER ").append(node.superName).append(System.lineSeparator());
        }
        for (String interfaceName : node.interfaces) {
            out.append("INTERFACE ").append(interfaceName).append(System.lineSeparator());
        }
        out.append(System.lineSeparator());

        for (FieldNode field : node.fields) {
            out.append("FIELD ").append(AccessFlagUtil.memberAccessToText(field.access));
            if (!AccessFlagUtil.memberAccessToText(field.access).isBlank()) {
                out.append(' ');
            }
            out.append(field.name).append(' ').append(field.desc);
            if (field.value != null) {
                out.append(" = ").append(formatConstant(field.value));
            }
            out.append(System.lineSeparator());
        }
        if (!node.fields.isEmpty()) {
            out.append(System.lineSeparator());
        }

        for (MethodNode method : node.methods) {
            out.append("METHOD ").append(AccessFlagUtil.memberAccessToText(method.access));
            if (!AccessFlagUtil.memberAccessToText(method.access).isBlank()) {
                out.append(' ');
            }
            out.append(method.name).append(' ').append(method.desc).append(System.lineSeparator());
            LabelNames labels = new LabelNames(method);
            for (AbstractInsnNode instruction : method.instructions) {
                appendInstruction(out, instruction, labels);
            }
            out.append("END").append(System.lineSeparator()).append(System.lineSeparator());
        }
        return out.toString();
    }

    private static void appendInstruction(StringBuilder out, AbstractInsnNode instruction, LabelNames labels) {
        if (instruction instanceof FrameNode) {
            return;
        }
        if (instruction instanceof LabelNode label) {
            out.append("  LABEL ").append(labels.name(label)).append(System.lineSeparator());
            return;
        }
        if (instruction instanceof LineNumberNode line) {
            out.append("  LINE ").append(line.line).append(' ').append(labels.name(line.start)).append(System.lineSeparator());
            return;
        }
        if (instruction instanceof InsnNode insn) {
            out.append("  ").append(opcode(insn.getOpcode())).append(System.lineSeparator());
            return;
        }
        if (instruction instanceof VarInsnNode var) {
            out.append("  ").append(opcode(var.getOpcode())).append(' ').append(var.var).append(System.lineSeparator());
            return;
        }
        if (instruction instanceof IntInsnNode intInsn) {
            out.append("  ").append(opcode(intInsn.getOpcode())).append(' ').append(intInsn.operand).append(System.lineSeparator());
            return;
        }
        if (instruction instanceof LdcInsnNode ldc) {
            out.append("  LDC ").append(formatConstant(ldc.cst)).append(System.lineSeparator());
            return;
        }
        if (instruction instanceof FieldInsnNode field) {
            out.append("  ").append(opcode(field.getOpcode())).append(' ')
                    .append(field.owner).append(' ')
                    .append(field.name).append(' ')
                    .append(field.desc).append(System.lineSeparator());
            return;
        }
        if (instruction instanceof MethodInsnNode method) {
            out.append("  ").append(opcode(method.getOpcode())).append(' ')
                    .append(method.owner).append(' ')
                    .append(method.name).append(' ')
                    .append(method.desc);
            if (method.itf) {
                out.append(" true");
            }
            out.append(System.lineSeparator());
            return;
        }
        if (instruction instanceof TypeInsnNode typeInsn) {
            out.append("  ").append(opcode(typeInsn.getOpcode())).append(' ')
                    .append(typeInsn.desc).append(System.lineSeparator());
            return;
        }
        if (instruction instanceof JumpInsnNode jump) {
            out.append("  ").append(opcode(jump.getOpcode())).append(' ')
                    .append(labels.name(jump.label)).append(System.lineSeparator());
            return;
        }
        if (instruction instanceof IincInsnNode iinc) {
            out.append("  IINC ").append(iinc.var).append(' ').append(iinc.incr).append(System.lineSeparator());
            return;
        }
        if (instruction instanceof MultiANewArrayInsnNode multi) {
            out.append("  MULTIANEWARRAY ").append(multi.desc).append(' ')
                    .append(multi.dims).append(System.lineSeparator());
            return;
        }
        if (instruction instanceof InvokeDynamicInsnNode indy) {
            out.append("  INVOKEDYNAMIC ").append(indy.name).append(' ')
                    .append(indy.desc).append(" ; bootstrap=").append(formatHandle(indy.bsm))
                    .append(System.lineSeparator());
            return;
        }
        if (instruction instanceof TableSwitchInsnNode tableSwitch) {
            out.append("  ; TABLESWITCH min=").append(tableSwitch.min)
                    .append(" max=").append(tableSwitch.max)
                    .append(" default=").append(labels.name(tableSwitch.dflt))
                    .append(System.lineSeparator());
            return;
        }
        if (instruction instanceof LookupSwitchInsnNode lookupSwitch) {
            out.append("  ; LOOKUPSWITCH keys=").append(lookupSwitch.keys)
                    .append(" default=").append(labels.name(lookupSwitch.dflt))
                    .append(System.lineSeparator());
        }
    }

    private static String opcode(int opcode) {
        if (opcode < 0 || opcode >= Printer.OPCODES.length) {
            return "UNKNOWN";
        }
        return Printer.OPCODES[opcode];
    }

    public static String formatConstant(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String string) {
            return '"' + escape(string) + '"';
        }
        if (value instanceof Long longValue) {
            return longValue + "L";
        }
        if (value instanceof Float floatValue) {
            return floatValue + "F";
        }
        if (value instanceof Double doubleValue) {
            return doubleValue + "D";
        }
        if (value instanceof Type type) {
            return "type:" + type.getDescriptor();
        }
        if (value instanceof Handle handle) {
            return "handle:" + formatHandle(handle);
        }
        return value.toString();
    }

    public static String escape(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> builder.append(c);
            }
        }
        return builder.toString();
    }

    private static String formatHandle(Handle handle) {
        return handle.getOwner() + "." + handle.getName() + handle.getDesc();
    }

    private static final class LabelNames {
        private final Map<LabelNode, String> names = new IdentityHashMap<>();
        private int next;

        private LabelNames(MethodNode method) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof LabelNode label) {
                    name(label);
                } else if (instruction instanceof JumpInsnNode jump) {
                    name(jump.label);
                } else if (instruction instanceof LineNumberNode line) {
                    name(line.start);
                } else if (instruction instanceof TableSwitchInsnNode tableSwitch) {
                    name(tableSwitch.dflt);
                    tableSwitch.labels.forEach(this::name);
                } else if (instruction instanceof LookupSwitchInsnNode lookupSwitch) {
                    name(lookupSwitch.dflt);
                    lookupSwitch.labels.forEach(this::name);
                }
            }
        }

        private String name(LabelNode label) {
            return names.computeIfAbsent(label, ignored -> "L" + next++);
        }
    }
}
