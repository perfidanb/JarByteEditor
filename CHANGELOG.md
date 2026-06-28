# Changelog

All notable changes to this project will be documented in this file.

## [1.0.2] - 2026-06-29

### Added
- **Android APK & DEX Support**: Directly open `.apk` files and edit `classes.dex` using the Smali assembler/disassembler (`dexlib2`, `smali`, `baksmali`).
- **In-Memory Virtual Tree for Smali**: Replaced the disk-heavy Smali extraction logic with an on-the-fly virtual memory mapping. This completely eliminates 100% disk usage errors, prevents OS crashes, and significantly reduces RAM overhead when handling large APKs (gigabyte-scale).
- **Dual-Protection Watchdog**: Added a background thread that monitors available Disk and RAM during critical operations, automatically terminating processes if resources fall below safe thresholds (Anti-Crash mechanism).
- **Floating Search Results Modal**: Global searches now open a non-modal popup containing a list of matches.
- **Search Line Navigation**: Search results in text resources (including `.smali` and `.jasm`) now compute the exact line number. Double-clicking a search result automatically opens the file and jumps the editor cursor to the correct line.
- **Auto-Assemble on Export**: When exporting/saving an APK, only modified Smali files are patched and re-assembled back into the `.dex` container via a lightweight temporary directory workflow.

### Fixed
- Fixed an issue where extracting multiple `.dex` files simultaneously would cause disk space exhaustion and crash the user's machine.
- Fixed `IndentingWriter` dependency mismatch by explicitly utilizing `BaksmaliWriter` directly for Smali formatting.
