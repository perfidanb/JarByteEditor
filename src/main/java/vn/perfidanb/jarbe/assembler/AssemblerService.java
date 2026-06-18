package vn.perfidanb.jarbe.assembler;

import vn.perfidanb.jarbe.asm.SimpleJasmAssembler;
import vn.perfidanb.jarbe.asm.SimpleJasmDisassembler;

public final class AssemblerService {
    private final SimpleJasmDisassembler disassembler = new SimpleJasmDisassembler();
    private final SimpleJasmAssembler assembler = new SimpleJasmAssembler();

    public String disassemble(byte[] classBytes) {
        return disassembler.disassemble(classBytes);
    }

    public byte[] assemble(String jasm, byte[] originalBytes, Integer targetJava) {
        return assembler.assemble(jasm, originalBytes, targetJava);
    }
}
