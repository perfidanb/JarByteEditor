# JarByteEditor

JarByteEditor is a Java 21 desktop and CLI tool for opening `.jar` / `.zip` / `.apk` files, inspecting JVM bytecode with ASM, editing Android `.dex` bytecode via Smali, editing `.jasm` text, editing resources, and packaging the result back into a jar or apk.

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

- Modern, dark JavaFX layout powered by the **AtlantaFX PrimerDark** theme.
- **Android APK & DEX Support**: Open `.apk` files directly. `.dex` files are parsed into a **Virtual In-Memory Tree** using `dexlib2`, allowing you to instantly view and edit `.smali` code without extracting gigabytes of files to disk.
- **Anti-Crash & Disk Protection**: Dual-protection watchdog monitors RAM and Disk space dynamically during heavy operations, preventing 100% disk usage and OS crashes.
- **Advanced Search & Navigation**: Floating (non-modal) search results popup. Compute exact line numbers for text resources and double-click to instantly jump to the matching line in the editor.
- **Chrome-style Multi-Tab Editor**: Open and edit multiple `.class`, `.smali` or resource files simultaneously without losing state.
- `Open JAR` for `.jar`, `.zip`, and `.apk`.
- **Dual-Mode Class Editing**: View and edit classes in either `.jasm` bytecode assembly or full **Java source code** (decompiled via CFR).
- **Live Java Compilation**: Edit Java source code and recompile on the fly. The compiler automatically uses a `libs` directory or custom JARs added via `Project > Manage Dependencies`.
- Editable text resources such as `yml`, `json`, `txt`, `xml`, and `properties`.
- Binary metadata viewer with path, size, SHA1, and modified state.
- Lazy JAR/APK loading with progress/cancel, safe large-file preview, class disassembly only after selection, and one-entry-at-a-time translation scanning to avoid GUI freezes on heavy archives.
- Save current editor buffer (compiles Java or assembles JASM/Smali) into the in-memory project with `Ctrl+S`.
- Save all modified tabs and export project with `Ctrl+Shift+S`.
- Save As JAR / APK. (Auto-assembles modified `.smali` back into `.dex` files).
- Export Project with raw classes, resources, `.jasm`, `.smali`, and `project.json`.
- Search, replace string, diff, statistics, call graph, annotation display, and constant pool table.
- Translate Project workflow with Google Translate preview, per-string checkboxes, and apply-to-memory changes.

## Translate Project

Open a jar, then use:

```txt
Tools > Translate Project
```

Select source and target languages, for example `English` to `Vietnamese`. JarByteEditor scans text resources and class string constants, sends candidate strings to Google Translate, then shows a preview table:

- Tick rows you want to keep.
- Untick rows you do not want to apply.
- Click `Apply Selected`.
- Export with `File > Save As JAR`.

JarByteEditor uses the Google Translate web domain, matching the browser flow:

```txt
https://translate.google.com.vn/?sl=en&tl=vi&op=translate
```

Internally it requests the same Google Translate web service on `translate.google.com.vn`, without requiring a Cloud Translation API key.

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

## Bytecode & Compilation Policy

JarByteEditor provides two distinct modes for editing Java classes:

1. **JASM (Bytecode Assembly)**:
   Class edits in JASM are handled with ASM and written with:
   ```java
   ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS
   ```
   The loader keeps class metadata in ASM tree form, including signatures, modules, records, nests, permitted subclasses, annotations, inner classes, source attributes, bootstrap methods, and debug tables when the corresponding methods are not rewritten.

2. **Java Mode**:
   JarByteEditor integrates the **CFR Decompiler** to convert bytecode back into readable Java source.
   When editing in Java mode, pressing `Ctrl+S` passes the code to the standard `javax.tools.JavaCompiler`. 
   To prevent `ClassNotFoundException` during live compilation, JarByteEditor automatically adds `.jar` files from a `libs` directory adjacent to your target JAR, and allows you to manually attach extra dependencies via the **Project > Manage Dependencies** menu.

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
