# Changelog

All notable changes to this project will be documented in this file.

## [1.0.2] - 2026-06-24

### Added
- **CFR Decompiler Integration**: You can now decompile class files into Java source code directly within the editor.
- **Live Java Compilation**: Edit decompiled Java code and seamlessly compile it back into bytecode with `Ctrl+S`.
- **Compiler Dependency Manager**: Easily add extra `.jar` files to your compilation classpath via `Project > Manage Dependencies`.
- **Multi-Tab Workspace**: Introduced a Chrome-style tabbed editor allowing you to work on multiple class or resource files simultaneously without losing state.
- **Java Syntax Highlighting**: Added a full-fledged highlighting engine for Java syntax when in Java editing mode.
- **Modern UI Theme**: Completely modernized the user interface with the `AtlantaFX PrimerDark` theme for a sleek, developer-friendly experience.
- **Auto-Scanning `libs` Folder**: The compiler now automatically scans for a `libs` directory adjacent to your target JAR file to populate the classpath.
- **Save Actions**: Added `Ctrl+S` to compile/assemble the active tab, and `Ctrl+Shift+S` to save all modified tabs and execute a project export.

### Changed
- The bytecode editing engine no longer restricts you to just `.jasm`. Users can seamlessly switch between `.jasm` assembly and full Java mode.
- The global details and constant pool views have been reworked to dynamically sync with the currently active tab.
