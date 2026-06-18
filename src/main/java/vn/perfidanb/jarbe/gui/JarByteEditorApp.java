package vn.perfidanb.jarbe.gui;

import javafx.application.Application;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.objectweb.asm.util.Printer;
import vn.perfidanb.jarbe.asm.AccessFlagUtil;
import vn.perfidanb.jarbe.asm.AsmClassAnalyzer;
import vn.perfidanb.jarbe.asm.ClassVersion;
import vn.perfidanb.jarbe.asm.ConstantPoolParser;
import vn.perfidanb.jarbe.diff.DiffEngine;
import vn.perfidanb.jarbe.editor.EditorSession;
import vn.perfidanb.jarbe.model.ClassSummary;
import vn.perfidanb.jarbe.model.ConstantPoolEntry;
import vn.perfidanb.jarbe.model.DiffReport;
import vn.perfidanb.jarbe.model.EntryType;
import vn.perfidanb.jarbe.model.JarEntryData;
import vn.perfidanb.jarbe.model.JarProject;
import vn.perfidanb.jarbe.model.ProjectStats;
import vn.perfidanb.jarbe.model.SearchResult;
import vn.perfidanb.jarbe.search.SearchEngine;
import vn.perfidanb.jarbe.search.SearchType;
import vn.perfidanb.jarbe.service.CallGraphService;
import vn.perfidanb.jarbe.service.JarProjectService;
import vn.perfidanb.jarbe.service.StatisticsEngine;
import vn.perfidanb.jarbe.util.FileTypeUtil;
import vn.perfidanb.jarbe.util.HashUtil;
import vn.perfidanb.jarbe.util.HumanSize;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class JarByteEditorApp extends Application {
    private static final String[] JASM_KEYWORDS = {
            "VERSION", "CLASS", "SUPER", "INTERFACE", "FIELD", "METHOD", "END", "LABEL", "LINE"
    };
    private static final String[] JASM_ACCESS_WORDS = {
            "public", "private", "protected", "static", "final", "abstract", "native", "synchronized",
            "record", "enum", "interface", "strict", "volatile", "transient", "synthetic", "annotation"
    };
    private static final Pattern JASM_HIGHLIGHT_PATTERN = Pattern.compile(
            "(?<COMMENT>;[^\\n]*)"
                    + "|(?<STRING>\"(?:\\\\.|[^\"\\\\])*\")"
                    + "|(?<KEYWORD>\\b(?:" + String.join("|", JASM_KEYWORDS) + ")\\b)"
                    + "|(?<OPCODE>\\b(?:" + opcodePattern() + ")\\b)"
                    + "|(?<ACCESS>\\b(?:" + String.join("|", JASM_ACCESS_WORDS) + ")\\b)"
                    + "|(?<DESCRIPTOR>\\([^\\s)]*\\)[^\\s]+|\\b\\[*(?:[BCDFIJSZV]|L[\\w/$]+;)\\b)"
                    + "|(?<OWNER>\\b(?:[A-Za-z_$][\\w$]*/)+(?:[A-Za-z_$][\\w$]*)\\b)"
                    + "|(?<NUMBER>\\b-?\\d+(?:L|F|D)?\\b)"
                    + "|(?<LABEL>\\bL\\d+\\b)"
    );

    private final JarProjectService projectService = new JarProjectService();
    private final StatisticsEngine statisticsEngine = new StatisticsEngine();
    private final SearchEngine searchEngine = new SearchEngine();
    private final CallGraphService callGraphService = new CallGraphService();
    private final DiffEngine diffEngine = new DiffEngine();
    private final AsmClassAnalyzer analyzer = new AsmClassAnalyzer();
    private final ConstantPoolParser constantPoolParser = new ConstantPoolParser();

    private Stage stage;
    private JarProject project;
    private EditorSession editorSession;
    private JarEntryData currentEntry;
    private boolean loadingEditor;
    private boolean dirty;

    private final TreeView<String> tree = new TreeView<>();
    private final CodeArea editor = new CodeArea();
    private final TextArea details = readOnlyArea();
    private final TextArea searchResults = readOnlyArea();
    private final TextArea diffResults = readOnlyArea();
    private final TextArea stats = readOnlyArea();
    private final TextArea callGraph = readOnlyArea();
    private final TableView<ConstantPoolEntry> constantPoolTable = new TableView<>();
    private final Label status = new Label("Ready");
    private final ProgressIndicator progress = new ProgressIndicator();
    private final ComboBox<String> target = new ComboBox<>();
    private double editorFontSize = 13.0;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        stage.setTitle("JarByteEditor");
        ResponsiveMetrics metrics = ResponsiveMetrics.from(Screen.getPrimary().getVisualBounds());
        editorFontSize = metrics.editorFontSize();
        BorderPane root = buildRoot();
        Scene scene = new Scene(root, metrics.width(), metrics.height());
        scene.getStylesheets().add(getClass().getResource("/vn/perfidanb/jarbe/gui/dark.css").toExternalForm());
        root.setStyle("-fx-font-size: " + metrics.uiFontSize() + "px;");
        stage.setScene(scene);
        configureStageBounds(metrics);
        stage.show();
    }

    private BorderPane buildRoot() {
        BorderPane root = new BorderPane();
        root.setTop(new VBox(buildMenu(), buildToolbar()));
        root.setCenter(buildMainSplit());
        root.setBottom(buildStatusBar());
        configureEditor();
        configureConstantPoolTable();
        return root;
    }

    private MenuBar buildMenu() {
        Menu file = new Menu("File");
        MenuItem open = item("Open JAR", this::openJar);
        MenuItem save = item("Save", this::saveCurrent);
        save.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
        MenuItem saveAs = item("Save As JAR", this::saveAsJar);
        MenuItem export = item("Export Project", this::exportProject);
        file.getItems().addAll(open, save, saveAs, export);

        Menu edit = new Menu("Edit");
        edit.getItems().addAll(
                item("Undo", editor::undo),
                item("Redo", editor::redo),
                item("Copy", editor::copy),
                item("Paste", editor::paste),
                item("Find", this::showFindDialog),
                item("Replace String", this::showReplaceDialog)
        );

        Menu view = new Menu("View");
        view.getItems().addAll(
                item("Refresh Colors", this::refreshHighlight),
                item("Refresh Statistics", this::refreshProjectViews),
                item("Refresh Call Graph", this::refreshProjectViews)
        );

        return new MenuBar(file, edit, view);
    }

    private ToolBar buildToolbar() {
        Button open = new Button("Open JAR");
        open.setOnAction(event -> openJar());
        Button save = new Button("Save");
        save.setOnAction(event -> saveCurrent());
        Button saveAs = new Button("Save As JAR");
        saveAs.setOnAction(event -> saveAsJar());
        Button export = new Button("Export");
        export.setOnAction(event -> exportProject());
        Button find = new Button("Find");
        find.setOnAction(event -> showFindDialog());
        Button replace = new Button("Replace");
        replace.setOnAction(event -> showReplaceDialog());

        target.getItems().addAll("Original", "8", "11", "17", "21", "25");
        target.getSelectionModel().selectFirst();
        target.setMinWidth(92);
        target.setPrefWidth(110);
        progress.setVisible(false);
        progress.setPrefSize(22, 22);
        ToolBar toolBar = new ToolBar(open, save, saveAs, export, find, replace, new Label("Target"), target, progress);
        toolBar.setPrefHeight(38);
        return toolBar;
    }

    private SplitPane buildMainSplit() {
        tree.setShowRoot(true);
        tree.setRoot(new TreeItem<>("No project"));
        tree.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected instanceof EntryTreeItem item) {
                showEntry(item.path());
            }
        });

        TabPane tabs = new TabPane();
        tabs.getTabs().add(tab("Editor", new VirtualizedScrollPane<>(editor)));
        tabs.getTabs().add(tab("Details", details));
        tabs.getTabs().add(tab("Constant Pool", constantPoolTable));
        tabs.getTabs().add(tab("Search", searchResults));
        tabs.getTabs().add(tab("Diff", diffResults));
        tabs.getTabs().add(tab("Statistics", stats));
        tabs.getTabs().add(tab("Call Graph", callGraph));
        tabs.getTabs().forEach(tab -> tab.setClosable(false));

        tree.setMinWidth(180);
        tree.setPrefWidth(320);
        tabs.setMinWidth(520);

        SplitPane split = new SplitPane(tree, tabs);
        split.widthProperty().addListener((obs, oldWidth, newWidth) -> {
            double width = newWidth.doubleValue();
            if (width > 0) {
                split.setDividerPositions(width < 980 ? 0.34 : width > 1600 ? 0.22 : 0.28);
            }
        });
        split.setDividerPositions(0.28);
        return split;
    }

    private HBox buildStatusBar() {
        HBox box = new HBox(status);
        box.setPadding(new Insets(6, 10, 6, 10));
        HBox.setHgrow(status, Priority.ALWAYS);
        return box;
    }

    private void configureEditor() {
        editor.setWrapText(false);
        editor.setParagraphGraphicFactory(LineNumberFactory.get(editor));
        editor.getStyleClass().add("bytecode-editor");
        editor.setStyle(editorFontStyle());
        editor.textProperty().addListener((obs, old, text) -> {
            if (!loadingEditor && currentEntry != null && editor.isEditable()) {
                dirty = true;
                status.setText("Modified: " + currentEntry.path());
            }
        });
        editor.multiPlainChanges()
                .successionEnds(Duration.ofMillis(120))
                .subscribe(ignore -> refreshHighlight());
    }

    private void configureConstantPoolTable() {
        TableColumn<ConstantPoolEntry, Number> index = new TableColumn<>("#");
        index.setCellValueFactory(value -> new SimpleIntegerProperty(value.getValue().index()));
        index.setPrefWidth(70);
        TableColumn<ConstantPoolEntry, String> kind = new TableColumn<>("Kind");
        kind.setCellValueFactory(value -> new SimpleStringProperty(value.getValue().kind()));
        kind.setPrefWidth(150);
        TableColumn<ConstantPoolEntry, String> value = new TableColumn<>("Value");
        value.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().value()));
        value.prefWidthProperty().bind(constantPoolTable.widthProperty()
                .subtract(index.widthProperty())
                .subtract(kind.widthProperty())
                .subtract(32));
        constantPoolTable.getColumns().add(index);
        constantPoolTable.getColumns().add(kind);
        constantPoolTable.getColumns().add(value);
    }

    private void openJar() {
        if (!confirmDiscardIfDirty()) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open JAR or ZIP");
        chooser.getExtensionFilters().add(new ExtensionFilter("JAR / ZIP", "*.jar", "*.zip"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        runTask("Opening " + file.getName(), new Task<JarProject>() {
            @Override
            protected JarProject call() throws Exception {
                return projectService.open(file.toPath());
            }
        }, opened -> {
            project = opened;
            editorSession = new EditorSession(project);
            currentEntry = null;
            dirty = false;
            buildTree();
            refreshProjectViews();
            status.setText("Opened " + project.displayName());
        });
    }

    private void saveCurrent() {
        if (currentEntry == null || !editor.isEditable()) {
            status.setText("Nothing editable is selected");
            return;
        }
        try {
            editorSession.applyText(currentEntry, editor.getText(), selectedTarget());
            dirty = false;
            refreshEntryDetails(currentEntry);
            refreshProjectViews();
            status.setText("Saved in memory: " + currentEntry.path());
        } catch (RuntimeException e) {
            showError("Invalid bytecode", e.getMessage());
        }
    }

    private void saveAsJar() {
        if (project == null) {
            showInfo("No project", "Open a jar first.");
            return;
        }
        if (dirty && !askYesNo("Save current editor changes before exporting?")) {
            return;
        }
        if (dirty) {
            saveCurrent();
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save As JAR");
        chooser.getExtensionFilters().add(new ExtensionFilter("JAR", "*.jar"));
        chooser.setInitialFileName(defaultOutputName());
        File output = chooser.showSaveDialog(stage);
        if (output == null) {
            return;
        }
        runTask("Building jar", new Task<Path>() {
            @Override
            protected Path call() throws Exception {
                projectService.saveAsJar(project, output.toPath());
                return output.toPath();
            }
        }, path -> status.setText("Built " + path));
    }

    private void exportProject() {
        if (project == null) {
            showInfo("No project", "Open a jar first.");
            return;
        }
        if (dirty) {
            saveCurrent();
        }
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Export Project");
        File dir = chooser.showDialog(stage);
        if (dir == null) {
            return;
        }
        runTask("Exporting project", new Task<Path>() {
            @Override
            protected Path call() throws Exception {
                projectService.exportProject(project, dir.toPath());
                return dir.toPath();
            }
        }, path -> status.setText("Exported " + path));
    }

    private void showEntry(String path) {
        if (project == null || path == null || path.isBlank()) {
            return;
        }
        if (!confirmDiscardIfDirty()) {
            tree.getSelectionModel().clearSelection();
            return;
        }
        project.find(path).ifPresent(entry -> {
            currentEntry = entry;
            loadingEditor = true;
            try {
                if (entry.type() == EntryType.CLASS || entry.type() == EntryType.TEXT_RESOURCE) {
                    editor.setEditable(true);
                    setEditorText(editorSession.editableText(entry));
                } else {
                    editor.setEditable(false);
                    setEditorText(binaryInfo(entry));
                }
                dirty = false;
                refreshEntryDetails(entry);
                refreshHighlight();
                status.setText("Selected " + entry.path());
            } catch (RuntimeException e) {
                editor.setEditable(false);
                setEditorText(binaryInfo(entry) + System.lineSeparator() + "ASM parse error: " + e.getMessage());
                refreshEntryDetails(entry);
            } finally {
                loadingEditor = false;
            }
        });
    }

    private void refreshEntryDetails(JarEntryData entry) {
        constantPoolTable.getItems().clear();
        StringBuilder text = new StringBuilder(binaryInfo(entry)).append(System.lineSeparator());
        if (entry.type() == EntryType.CLASS) {
            try {
                ClassSummary summary = analyzer.analyze(entry.bytes());
                text.append(System.lineSeparator())
                        .append("Class: ").append(summary.name()).append(System.lineSeparator())
                        .append("Super: ").append(summary.superName()).append(System.lineSeparator())
                        .append("Version: ").append(ClassVersion.display(summary.majorVersion())).append(System.lineSeparator())
                        .append("Access: ").append(AccessFlagUtil.classAccessToText(summary.access())).append(System.lineSeparator())
                        .append("Methods: ").append(summary.methods().size()).append(System.lineSeparator())
                        .append("Fields: ").append(summary.fields().size()).append(System.lineSeparator())
                        .append("Instructions: ").append(summary.instructionCount()).append(System.lineSeparator());
                if (!summary.annotations().isEmpty()) {
                    text.append("Annotations: ").append(String.join(", ", summary.annotations())).append(System.lineSeparator());
                }
                constantPoolTable.getItems().setAll(constantPoolParser.parse(entry.bytes()));
            } catch (RuntimeException e) {
                text.append(System.lineSeparator()).append("ASM parse error: ").append(e.getMessage()).append(System.lineSeparator());
            }
        }
        details.setText(text.toString());
    }

    private void refreshHighlight() {
        String text = editor.getText() == null ? "" : editor.getText();
        editor.setStyleSpans(0, computeHighlighting(text));
    }

    private void refreshProjectViews() {
        if (project == null) {
            return;
        }
        ProjectStats projectStats = statisticsEngine.calculate(project);
        stats.setText(projectStats.toDisplayString());
        callGraph.setText(callGraphService.toDisplayString(project));
        diffResults.setText(diffEngine.diff(originalSnapshot(project), project).toDisplayString());
    }

    private JarProject originalSnapshot(JarProject source) {
        JarProject snapshot = new JarProject(source.sourcePath().orElse(null), source.displayName() + " original");
        for (JarEntryData entry : source.entries()) {
            snapshot.putEntry(new JarEntryData(entry.path(), entry.type(), entry.directory(), entry.originalBytes()));
        }
        return snapshot;
    }

    private void buildTree() {
        EntryTreeItem root = new EntryTreeItem(project.displayName(), null);
        Map<String, EntryTreeItem> nodes = new HashMap<>();
        nodes.put("", root);
        for (JarEntryData entry : project.sortedEntries()) {
            String[] parts = entry.path().split("/");
            String parentPath = "";
            EntryTreeItem parent = root;
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].isBlank()) {
                    continue;
                }
                String currentPath = parentPath.isBlank() ? parts[i] : parentPath + "/" + parts[i];
                boolean leaf = i == parts.length - 1;
                EntryTreeItem node = nodes.get(currentPath);
                if (node == null) {
                    node = new EntryTreeItem(parts[i], leaf ? entry.path() : currentPath + "/");
                    nodes.put(currentPath, node);
                    parent.getChildren().add(node);
                }
                parent = node;
                parentPath = currentPath;
            }
        }
        root.setExpanded(true);
        tree.setRoot(root);
    }

    private void showFindDialog() {
        if (project == null) {
            showInfo("No project", "Open a jar first.");
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Find");
        TextField query = new TextField();
        ComboBox<SearchType> type = new ComboBox<>();
        type.getItems().setAll(SearchType.values());
        type.getSelectionModel().select(SearchType.ALL);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.addRow(0, new Label("Find"), query);
        grid.addRow(1, new Label("Type"), type);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.showAndWait().filter(button -> button == ButtonType.OK).ifPresent(button -> {
            List<SearchResult> results = searchEngine.search(project, query.getText(), type.getValue());
            searchResults.setText(results.isEmpty()
                    ? "No results"
                    : String.join(System.lineSeparator(), results.stream().map(SearchResult::compact).toList()));
            status.setText("Search results: " + results.size());
        });
    }

    private void showReplaceDialog() {
        if (project == null) {
            showInfo("No project", "Open a jar first.");
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Replace String");
        TextField find = new TextField();
        TextField replacement = new TextField();
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.addRow(0, new Label("Find"), find);
        grid.addRow(1, new Label("Replace"), replacement);
        dialog.getDialogPane().setContent(grid);
        ButtonType preview = new ButtonType("Preview");
        ButtonType apply = new ButtonType("Apply");
        dialog.getDialogPane().getButtonTypes().addAll(preview, apply, ButtonType.CANCEL);
        dialog.showAndWait().ifPresent(button -> {
            if (find.getText() == null || find.getText().isEmpty()) {
                showError("Replace String", "Find text must not be empty");
                return;
            }
            if (button == preview) {
                List<SearchResult> results = searchEngine.search(project, find.getText(), SearchType.STRING);
                searchResults.setText(results.isEmpty()
                        ? "No results"
                        : String.join(System.lineSeparator(), results.stream().map(SearchResult::compact).toList()));
                status.setText("Preview matches: " + results.size());
            } else if (button == apply) {
                int changed = projectService.replaceString(project, find.getText(), replacement.getText(), selectedTarget());
                refreshProjectViews();
                if (currentEntry != null) {
                    showEntry(currentEntry.path());
                }
                status.setText("Replace applied to entries: " + changed);
            }
        });
    }

    private Integer selectedTarget() {
        String selected = target.getValue();
        if (selected == null || selected.equals("Original")) {
            return null;
        }
        return Integer.parseInt(selected);
    }

    private boolean confirmDiscardIfDirty() {
        if (!dirty) {
            return true;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Current editor changes are not saved in memory.", ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
        alert.setTitle("Unsaved Editor");
        alert.setHeaderText("Save current changes?");
        ButtonType result = alert.showAndWait().orElse(ButtonType.CANCEL);
        if (result == ButtonType.YES) {
            saveCurrent();
            return !dirty;
        }
        return result == ButtonType.NO;
    }

    private boolean askYesNo(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message == null ? title : message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.showAndWait();
    }

    private <T> void runTask(String message, Task<T> task, java.util.function.Consumer<T> onSuccess) {
        status.setText(message);
        progress.setVisible(true);
        task.setOnSucceeded(event -> {
            progress.setVisible(false);
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            progress.setVisible(false);
            Throwable error = task.getException();
            showError("Operation failed", error == null ? "Unknown error" : error.getMessage());
            status.setText("Failed: " + message);
        });
        Thread thread = new Thread(task, "jarbe-task");
        thread.setDaemon(true);
        thread.start();
    }

    private static MenuItem item(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(event -> action.run());
        return item;
    }

    private static Tab tab(String title, javafx.scene.Node node) {
        return new Tab(title, node);
    }

    private static TextArea readOnlyArea() {
        TextArea area = new TextArea();
        area.setEditable(false);
        area.setWrapText(false);
        area.setStyle("-fx-font-family: Consolas, 'JetBrains Mono', monospace;");
        return area;
    }

    private void configureStageBounds(ResponsiveMetrics metrics) {
        Rectangle2D bounds = metrics.visualBounds();
        stage.setMinWidth(Math.min(960, bounds.getWidth()));
        stage.setMinHeight(Math.min(620, bounds.getHeight()));
        stage.setWidth(metrics.width());
        stage.setHeight(metrics.height());
        stage.setX(bounds.getMinX() + (bounds.getWidth() - metrics.width()) / 2);
        stage.setY(bounds.getMinY() + (bounds.getHeight() - metrics.height()) / 2);
        if (bounds.getWidth() < 1100 || bounds.getHeight() < 720) {
            stage.setMaximized(true);
        }
    }

    private String editorFontStyle() {
        return "-fx-font-family: Consolas, 'JetBrains Mono', monospace; -fx-font-size: "
                + editorFontSize + "px;";
    }

    private void setEditorText(String text) {
        String value = text == null ? "" : text;
        editor.replaceText(0, editor.getLength(), value);
        refreshHighlight();
    }

    private static StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = JASM_HIGHLIGHT_PATTERN.matcher(text);
        int lastKeywordEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            String styleClass =
                    matcher.group("COMMENT") != null ? "jasm-comment" :
                    matcher.group("STRING") != null ? "jasm-string" :
                    matcher.group("KEYWORD") != null ? "jasm-keyword" :
                    matcher.group("OPCODE") != null ? "jasm-opcode" :
                    matcher.group("ACCESS") != null ? "jasm-access" :
                    matcher.group("DESCRIPTOR") != null ? "jasm-descriptor" :
                    matcher.group("OWNER") != null ? "jasm-owner" :
                    matcher.group("NUMBER") != null ? "jasm-number" :
                    matcher.group("LABEL") != null ? "jasm-label" :
                    "jasm-plain";
            spansBuilder.add(Collections.singleton("jasm-plain"), matcher.start() - lastKeywordEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKeywordEnd = matcher.end();
        }
        spansBuilder.add(Collections.singleton("jasm-plain"), text.length() - lastKeywordEnd);
        return spansBuilder.create();
    }

    private static String opcodePattern() {
        return Arrays.stream(Printer.OPCODES)
                .filter(name -> name != null && !name.isBlank())
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
    }

    private String binaryInfo(JarEntryData entry) {
        StringBuilder builder = new StringBuilder();
        builder.append("Path: ").append(entry.path()).append(System.lineSeparator())
                .append("Type: ").append(entry.type()).append(System.lineSeparator())
                .append("Size: ").append(HumanSize.format(entry.size())).append(System.lineSeparator())
                .append("SHA1: ").append(HashUtil.sha1(entry.bytes())).append(System.lineSeparator())
                .append("Modified: ").append(entry.modified()).append(System.lineSeparator());
        if (FileTypeUtil.isBinaryPreviewPath(entry.path())) {
            builder.append("Binary viewer: metadata only").append(System.lineSeparator());
        }
        return builder.toString();
    }

    private String defaultOutputName() {
        if (project == null) {
            return "plugin-fixed.jar";
        }
        String name = project.displayName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) + "-fixed.jar" : name + "-fixed.jar";
    }

    private static final class EntryTreeItem extends TreeItem<String> {
        private final String path;

        private EntryTreeItem(String value, String path) {
            super(value);
            this.path = path;
        }

        private String path() {
            return path;
        }
    }

    private record ResponsiveMetrics(Rectangle2D visualBounds, double width, double height, double uiFontSize, double editorFontSize) {
        private static ResponsiveMetrics from(Rectangle2D bounds) {
            double width = clamp(bounds.getWidth() * 0.92, 980, bounds.getWidth());
            double height = clamp(bounds.getHeight() * 0.90, 650, bounds.getHeight());
            double reference = Math.min(bounds.getWidth() / 1366.0, bounds.getHeight() / 768.0);
            double uiFontSize = clamp(12.0 * reference, 11.0, 15.0);
            double editorFontSize = clamp(13.0 * reference, 12.0, 17.0);
            return new ResponsiveMetrics(bounds, width, height, uiFontSize, editorFontSize);
        }

        private static double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
