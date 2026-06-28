package vn.perfidanb.jarbe;

import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.baksmali.BaksmaliOptions;
import org.jf.baksmali.Adaptors.ClassDefinition;
import org.jf.util.IndentingWriter;

import java.io.File;
import java.io.StringWriter;

public class TestBaksmali {
    public static void main(String[] args) throws Exception {
        org.jf.smali.SmaliOptions smaliOptions = new org.jf.smali.SmaliOptions();
        boolean success = org.jf.smali.Smali.assemble(smaliOptions, "in", "out.dex");
        System.out.println(success);
    }
}

