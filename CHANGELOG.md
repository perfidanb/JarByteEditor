# Changelog

All notable changes to JarByteEditor are documented here.

## 1.0.1

### Added

- Added JavaFX desktop GUI with dark theme, menu bar, toolbar, project tree, editor tabs, status bar, and responsive window scaling.
- Added syntax-highlighted `.jasm` editor for class bytecode using RichTextFX.
- Added grouped sidebar with root jar, `classes`, `files`, file-type icons, and sidebar search.
- Added Google Translate project workflow using the Google Translate web service on `translate.google.com.vn`, without a Cloud Translation API key.
- Added translation language selection, progress/log dialog, preview table, per-string checkboxes, and apply-selected behavior.
- Added CLI commands for listing, disassembling, assembling, replacing strings, searching, statistics, diff, and call graph.
- Added export workflow that writes raw classes, resources, `.jasm` files, and `project.json`.

### Changed

- Replaced colored toolbar squares with matching vector icons.
- Improved editor colors for a softer dark UI and more readable bytecode tokens.
- Changed JAR opening to lazy-load entries instead of reading every file into memory immediately.
- Changed class rendering so `.class` files are only disassembled when the user selects a class.
- Changed translation scanning to process entries one by one and avoid caching untouched class bytes in memory.
- Changed project statistics/call graph behavior in the GUI to avoid automatic deep parsing after opening large jars.
- Changed Find and Replace workflows to run in background tasks so the GUI stays responsive.
- Changed Save As JAR to stream unchanged entries from the source jar when possible.

### Fixed

- Fixed UI freezing risk when opening jars with many classes.
- Fixed large text/class preview by disabling unsafe inline editing and showing safe metadata/sample content.
- Fixed expensive SHA1 calculation for large lazy entries by deferring hash generation until needed.

### Tests

- Added translation tests for resource and class string replacement.
- Added lazy-load tests to verify large entries are not loaded during open/save.
- Added lazy translation scan test to verify class scan does not cache untouched class bytes.
