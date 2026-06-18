# JarByteEditor

JarByteEditor is a Java 21 desktop and CLI tool for opening `.jar` / `.zip` files, inspecting JVM bytecode with ASM, editing `.jasm` text, editing resources, and packaging the result back into a jar.

## Build

```bash
mvn clean package
```

The runnable jar is created at:

```txt
target/JarByteEditor.jar
```

The default JavaFX classifier is `win`. On another platform, build with:

```bash
mvn clean package -Djavafx.platform=linux
mvn clean package -Djavafx.platform=mac
```

## Run GUI

```bash
java -jar target/JarByteEditor.jar
```

The GUI provides:

- Dark JavaFX layout with menu bar, tree explorer, editor, status bar.
- `Open JAR` for `.jar` and `.zip`.
- Editable syntax-highlighted bytecode view for `.class` entries in `.jasm`.
- Editable text resources such as `yml`, `json`, `txt`, `xml`, and `properties`.
- Binary metadata viewer with path, size, SHA1, and modified state.
- Save current editor buffer into the in-memory project with `Ctrl+S`.
- Save As JAR.
- Export Project with raw classes, resources, `.jasm`, and `project.json`.
- Search, replace string, diff, statistics, call graph, annotation display, and constant pool table.

## CLI

The jar opens the GUI when no arguments are provided. With arguments it runs Picocli commands:

```bash
java -jar target/JarByteEditor.jar list plugin.jar
java -jar target/JarByteEditor.jar disasm plugin.jar output
java -jar target/JarByteEditor.jar asm output plugin-fixed.jar
java -jar target/JarByteEditor.jar replace-string input.jar old new output.jar
java -jar target/JarByteEditor.jar search plugin.jar "Hello"
java -jar target/JarByteEditor.jar stats plugin.jar
java -jar target/JarByteEditor.jar diff old.jar new.jar
java -jar target/JarByteEditor.jar callgraph plugin.jar
```

Commands that rebuild classes accept a target Java version:

```bash
java -jar target/JarByteEditor.jar asm output plugin-fixed.jar --target 21
java -jar target/JarByteEditor.jar replace-string input.jar old new output.jar --target 17
```

If `--target` is omitted, original class versions are preserved.

## Java Class Versions

| Java | Class Version |
| ---- | ------------- |
| 8 | 52 |
| 9 | 53 |
| 10 | 54 |
| 11 | 55 |
| 12 | 56 |
| 13 | 57 |
| 14 | 58 |
| 15 | 59 |
| 16 | 60 |
| 17 | 61 |
| 18 | 62 |
| 19 | 63 |
| 20 | 64 |
| 21 | 65 |
| 22 | 66 |
| 23 | 67 |
| 24 | 68 |
| 25 | 69 |

## JASM Format

The editor uses a compact assembler format:

```asm
VERSION 61
CLASS public com/example/Main
SUPER java/lang/Object

FIELD private message Ljava/lang/String;

METHOD public onEnable ()V
  ALOAD 0
  LDC "Enabled"
  RETURN
END
```

Supported editable instruction families include no-argument opcodes, variable opcodes, integer opcodes, field calls, method calls, type instructions, jumps, labels, line numbers, `LDC`, `IINC`, and `MULTIANEWARRAY`. Classes with advanced instructions are still opened and exported; unchanged class bytes are preserved during exported-project assembly.

## Bytecode Policy

JarByteEditor does not decompile Java source and does not compile Java source back to classes. Class edits are handled with ASM and written with:

```java
ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS
```

The loader keeps class metadata in ASM tree form, including signatures, modules, records, nests, permitted subclasses, annotations, inner classes, source attributes, bootstrap methods, and debug tables when the corresponding methods are not rewritten.

## Project Export

`disasm` and GUI export create:

```txt
project/
  classes/
    com/example/Main.class
    com/example/Main.class.jasm
  resources/
    plugin.yml
  project.json
```

`asm project plugin-fixed.jar` rebuilds the jar. If a `.jasm` file has not changed, the original `.class` bytes are copied back exactly. If a `.jasm` file changed, the class is assembled through ASM.
