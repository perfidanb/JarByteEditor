package vn.perfidanb.jarbe.gui;

import javafx.application.Application;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.TreeCell;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
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
import vn.perfidanb.jarbe.editor.EditorSession;
import vn.perfidanb.jarbe.model.ClassSummary;
import vn.perfidanb.jarbe.model.ConstantPoolEntry;
import vn.perfidanb.jarbe.model.EntryType;
import vn.perfidanb.jarbe.model.JarEntryData;
import vn.perfidanb.jarbe.model.JarProject;
import vn.perfidanb.jarbe.model.SearchResult;
import vn.perfidanb.jarbe.model.TranslationCandidate;
import vn.perfidanb.jarbe.search.SearchEngine;
import vn.perfidanb.jarbe.search.SearchType;
import vn.perfidanb.jarbe.service.GoogleTranslateClient;
import vn.perfidanb.jarbe.service.JarProjectService;
import vn.perfidanb.jarbe.service.TranslationEngine;
import vn.perfidanb.jarbe.util.FileTypeUtil;
import vn.perfidanb.jarbe.util.HashUtil;
import vn.perfidanb.jarbe.util.HumanSize;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
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

    private static final String[] JAVA_KEYWORDS = new String[]{
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
            "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "var", "record", "yield"
    };

    private static final Pattern JAVA_HIGHLIGHT_PATTERN = Pattern.compile(
            "(?<COMMENT>//[^\\n]*|/\\*(?:.|\\R)*?\\*/)"
                    + "|(?<STRING>\"(?:\\\\.|[^\"\\\\])*\")"
                    + "|(?<KEYWORD>\\b(?:" + String.join("|", JAVA_KEYWORDS) + ")\\b)"
                    + "|(?<ANNOTATION>@[\\w]+)"
                    + "|(?<NUMBER>\\b\\d+(?:\\.\\d+)?(?:[fFdDlL])?\\b)"
    );
    private static final List<LanguageOption> SOURCE_LANGUAGES = List.of(
            new LanguageOption("Auto detect", "auto"),
            new LanguageOption("English", "en"),
            new LanguageOption("Vietnamese", "vi"),
            new LanguageOption("Japanese", "ja"),
            new LanguageOption("Korean", "ko"),
            new LanguageOption("Chinese Simplified", "zh-CN"),
            new LanguageOption("Thai", "th"),
            new LanguageOption("French", "fr"),
            new LanguageOption("German", "de"),
            new LanguageOption("Spanish", "es"),
            new LanguageOption("Russian", "ru"),
            new LanguageOption("Indonesian", "id")
    );
    private static final List<LanguageOption> TARGET_LANGUAGES = SOURCE_LANGUAGES.stream()
            .filter(language -> !language.code().equals("auto"))
            .toList();
    private static final long MAX_TEXT_EDITOR_BYTES = 2L * 1024 * 1024;
    private static final long MAX_CLASS_EDITOR_BYTES = 4L * 1024 * 1024;
    private static final long MAX_CLASS_DETAILS_BYTES = 8L * 1024 * 1024;
    private static final long MAX_HASH_BYTES = 2L * 1024 * 1024;
    private static final long MAX_AUTO_ANALYZE_BYTES = 96L * 1024 * 1024;
    private static final int MAX_AUTO_ANALYZE_CLASSES = 2500;
    private static final int MAX_EDITOR_CHARS = 1_500_000;
    private static final int MAX_HIGHLIGHT_CHARS = 500_000;
    private static final int MAX_TREE_LEAVES_PER_GROUP = 30_000;

    private final JarProjectService projectService = new JarProjectService();
    private final SearchEngine searchEngine = new SearchEngine();
    private final AsmClassAnalyzer analyzer = new AsmClassAnalyzer();
    private final ConstantPoolParser constantPoolParser = new ConstantPoolParser();
    private final PauseTransition sidebarSearchDelay = new PauseTransition(javafx.util.Duration.millis(180));

    private Stage stage;
    private JarProject project;
    private EditorSession editorSession;
    private JarEntryData currentEntry;
    private boolean loadingEditor;
    private boolean highlightingEnabled = true;
    private boolean dirty;
    private Task<EntryTreeItem> treeBuildTask;
    private Task<EntryViewData> entryLoadTask;
    private Task<ProjectViewData> projectViewsTask;

    private final TreeView<String> tree = new TreeView<>();
    private final TextField sidebarSearch = new TextField();
    private final TabPane fileTabs = new TabPane();
    private final Map<String, FileTab> openTabs = new HashMap<>();
    private final TextArea details = readOnlyArea();
    private final ListView<SearchResult> searchResults = new ListView<>();
    private final TextArea diffResults = readOnlyArea();
    private final TextArea stats = readOnlyArea();
    private final TextArea callGraph = readOnlyArea();
    private final TableView<ConstantPoolEntry> constantPoolTable = new TableView<>();
    private final Label status = new Label("Ready");
    private final ProgressIndicator progress = new ProgressIndicator();
    // Target ComboBox removed from global state
    private double editorFontSize = 13.0;

    @Override
    public void start(Stage primaryStage) {
        javafx.application.Application.setUserAgentStylesheet(new atlantafx.base.theme.PrimerDark().getUserAgentStylesheet());
        this.stage = primaryStage;
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
        root.setTop(buildMenu());
        root.setCenter(buildMainSplit());
        root.setBottom(buildStatusBar());
        configureConstantPoolTable();
        return root;
    }

    private MenuBar buildMenu() {
        Menu file = new Menu("File");
        MenuItem open = item("Open File", this::openJar);
        MenuItem save = item("Save to Memory", this::saveCurrent);
        save.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
        MenuItem saveAs = item("Build / Export", this::saveAsJar);
        saveAs.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        MenuItem export = item("Export Project Dir", this::exportProject);
        file.getItems().addAll(open, save, saveAs, export);

        Menu edit = new Menu("Edit");
        edit.getItems().addAll(
                item("Undo", () -> { if (getActiveTab() != null) getActiveTab().getEditor().undo(); }),
                item("Redo", () -> { if (getActiveTab() != null) getActiveTab().getEditor().redo(); }),
                item("Copy", () -> { if (getActiveTab() != null) getActiveTab().getEditor().copy(); }),
                item("Paste", () -> { if (getActiveTab() != null) getActiveTab().getEditor().paste(); }),
                item("Find", this::showFindDialog),
                item("Replace String", this::showReplaceDialog)
        );

        Menu view = new Menu("View");
        view.getItems().addAll(
                item("Refresh Colors", () -> { if (getActiveTab() != null) refreshHighlight(getActiveTab()); }),
                item("Refresh Statistics", this::refreshProjectViews),
                item("Refresh Call Graph", this::refreshProjectViews)
        );

        Menu tools = new Menu("Tools");
        tools.getItems().addAll(
                item("Translate Project", this::showTranslateDialog),
                new SeparatorMenuItem(),
                item("Apktool Manager", () -> {
                    ApktoolDialog dialog = new ApktoolDialog(stage);
                    dialog.showAndWait();
                })
        );

        Menu projectMenu = new Menu("Project");
        projectMenu.getItems().addAll(
                item("Manage Dependencies", this::showDependenciesDialog)
        );
        return new MenuBar(file, edit, view, tools, projectMenu);
    }



    private SplitPane buildMainSplit() {
        tree.setShowRoot(true);
        tree.setRoot(new TreeItem<>("No project"));
        tree.getStyleClass().add("project-tree");
        tree.setCellFactory(view -> new TreeCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(item);
                if (getTreeItem() instanceof EntryTreeItem entryTreeItem) {
                    setGraphic(FileIconFactory.forTreeEntry(entryTreeItem.path(), item));
                } else {
                    setGraphic(FileIconFactory.forTreeEntry(null, item));
                }
            }
        });
        tree.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected instanceof EntryTreeItem item) {
                showEntry(item.path());
            }
        });

        TabPane toolsTabs = new TabPane();
        toolsTabs.getTabs().add(tab("Details", details));
        toolsTabs.getTabs().add(tab("Constant Pool", constantPoolTable));
        searchResults.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(SearchResult item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.compact());
                }
            }
        });
        searchResults.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                SearchResult selected = searchResults.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    showEntryAndJumpToLine(selected.path(), selected.line());
                }
            }
        });

        toolsTabs.getTabs().add(tab("Search", searchResults));
        toolsTabs.getTabs().add(tab("Diff", diffResults));
        toolsTabs.getTabs().add(tab("Statistics", stats));
        toolsTabs.getTabs().add(tab("Call Graph", callGraph));
        toolsTabs.getTabs().forEach(tab -> tab.setClosable(false));

        tree.setMinWidth(180);
        tree.setPrefWidth(320);
        toolsTabs.setMinWidth(520);

        fileTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        fileTabs.getSelectionModel().selectedItemProperty().addListener((obs, old, selectedTab) -> {
            if (selectedTab != null) {
                FileTab ft = (FileTab) selectedTab.getUserData();
                if (ft != null) {
                    currentEntry = ft.getEntry();
                    refreshEntryDetails(currentEntry);
                }
            } else {
                currentEntry = null;
            }
        });

        SplitPane centerSplit = new SplitPane(fileTabs, toolsTabs);
        centerSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
        centerSplit.setDividerPositions(0.7);

        BorderPane sidebar = buildSidebar();
        SplitPane split = new SplitPane(sidebar, centerSplit);
        split.widthProperty().addListener((obs, oldWidth, newWidth) -> {
            double width = newWidth.doubleValue();
            if (width > 0) {
                split.setDividerPositions(width < 980 ? 0.34 : width > 1600 ? 0.22 : 0.28);
            }
        });
        split.setDividerPositions(0.28);
        return split;
    }

    private FileTab getActiveTab() {
        Tab selected = fileTabs.getSelectionModel().getSelectedItem();
        return selected != null ? (FileTab) selected.getUserData() : null;
    }

    private BorderPane buildSidebar() {
        sidebarSearch.setPromptText("Search...");
        sidebarSearch.getStyleClass().add("sidebar-search");
        sidebarSearchDelay.setOnFinished(event -> buildTree());
        sidebarSearch.textProperty().addListener((obs, old, text) -> sidebarSearchDelay.playFromStart());

        Node searchIcon = FileIconFactory.actionIcon("find-action-icon");
        searchIcon.getStyleClass().add("sidebar-search-icon");
        searchIcon.setMouseTransparent(true);
        StackPane searchBox = new StackPane(sidebarSearch, searchIcon);
        searchBox.getStyleClass().add("sidebar-search-box");
        StackPane.setAlignment(searchIcon, Pos.CENTER_RIGHT);
        StackPane.setMargin(searchIcon, new Insets(0, 10, 0, 0));

        BorderPane sidebar = new BorderPane();
        sidebar.getStyleClass().add("sidebar");
        sidebar.setCenter(tree);
        sidebar.setBottom(searchBox);
        sidebar.setMinWidth(220);
        sidebar.setPrefWidth(330);
        return sidebar;
    }

    private HBox buildStatusBar() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        progress.setVisible(false);
        progress.setPrefSize(16, 16);
        HBox box = new HBox(5, status, spacer, progress);
        box.setPadding(new Insets(6, 10, 6, 10));
        return box;
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

    private void showDependenciesDialog() {
        if (project == null || editorSession == null) {
            showInfo("No Project", "Please open a JAR first.");
            return;
        }
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Manage Dependencies");
        dialog.setHeaderText("Add extra JAR files for Java Compilation");
        
        ListView<File> listView = new ListView<>(editorSession.getUserDependencies());
        
        Button addBtn = new Button("Add JAR");
        addBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new ExtensionFilter("JAR Files", "*.jar"));
            List<File> files = chooser.showOpenMultipleDialog(stage);
            if (files != null) {
                editorSession.getUserDependencies().addAll(files);
            }
        });
        
        Button removeBtn = new Button("Remove");
        removeBtn.setOnAction(e -> {
            File selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                editorSession.getUserDependencies().remove(selected);
            }
        });
        
        HBox btns = new HBox(10, addBtn, removeBtn);
        VBox vbox = new VBox(10, listView, btns);
        vbox.setPrefSize(400, 300);
        
        dialog.getDialogPane().setContent(vbox);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private void openJar() {
        if (!confirmDiscardIfDirty()) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open JAR, ZIP, or APK");
        chooser.getExtensionFilters().add(new ExtensionFilter("Archive Files", "*.jar", "*.zip", "*.apk"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }
        runOpenTask(file);
    }

    private void runOpenTask(File file) {
        Dialog<ButtonType> openDialog = new Dialog<>();
        openDialog.setTitle("Opening JAR");
        TextArea log = readOnlyArea();
        log.getStyleClass().add("translation-log");
        ProgressBar bar = new ProgressBar(0);
        bar.setMaxWidth(Double.MAX_VALUE);
        Label phase = new Label("Preparing...");
        VBox content = new VBox(10, phase, bar, log);
        content.setPrefSize(700, 360);
        VBox.setVgrow(log, Priority.ALWAYS);
        openDialog.getDialogPane().setContent(content);
        ButtonType cancel = new ButtonType("Cancel");
        openDialog.getDialogPane().getButtonTypes().add(cancel);

        Task<JarProject> task = new Task<>() {
            @Override
            protected JarProject call() throws Exception {
                updateMessage("Opening " + file.getName());
                JarProjectService.OpenProgress op = new JarProjectService.OpenProgress() {
                    @Override
                    public void update(int completed, int total, String entryName, long entrySize) {
                        updateProgress(completed, Math.max(total, 1));
                        if (completed == 1 || completed == total || completed % 50 == 0) {
                            updateMessage("Reading " + completed + "/" + total + ": " + entryName);
                        }
                    }
                };
                if (file.getName().toLowerCase().endsWith(".apk")) {
                    return projectService.openApk(file.toPath(), op);
                } else {
                    return projectService.open(file.toPath(), op);
                }
            }
        };

        phase.textProperty().bind(task.messageProperty());
        bar.progressProperty().bind(task.progressProperty());
        task.messageProperty().addListener((obs, old, message) -> {
            if (message != null && !message.isBlank()) {
                log.appendText(message + System.lineSeparator());
            }
        });
        openDialog.setOnCloseRequest(event -> {
            if (task.isRunning()) {
                task.cancel(true);
            }
        });
        openDialog.setResultConverter(button -> {
            if (button == cancel && task.isRunning()) {
                task.cancel(true);
                status.setText("Open cancelled");
            }
            return button;
        });

        status.setText("Opening " + file.getName());
        progress.setVisible(true);
        task.setOnSucceeded(event -> {
            progress.setVisible(false);
            openDialog.close();
            JarProject opened = task.getValue();
            project = opened;
            editorSession = new EditorSession(project);
            currentEntry = null;
            fileTabs.getTabs().clear();
            openTabs.clear();
            buildTree();
            refreshProjectViews();
            status.setText("Opened " + project.displayName());
        });
        task.setOnCancelled(event -> {
            progress.setVisible(false);
            openDialog.close();
            status.setText("Open cancelled");
        });
        task.setOnFailed(event -> {
            progress.setVisible(false);
            openDialog.close();
            Throwable error = task.getException();
            showError("Open failed", error == null ? "Unknown error" : error.getMessage());
            status.setText("Open failed");
        });

        Thread thread = new Thread(task, "jarbe-open");
        thread.setDaemon(true);
        thread.start();
        openDialog.show();
    }

    private void saveCurrent() {
        FileTab active = getActiveTab();
        if (active == null || !active.getEditor().isEditable()) {
            status.setText("Nothing editable is selected");
            return;
        }
        JarEntryData entry = active.getEntry();
        try {
            if (active.isJavaMode()) {
                editorSession.applyJavaText(entry, active.getEditor().getText());
            } else {
                editorSession.applyText(entry, active.getEditor().getText(), null);
            }
            active.setDirty(false);
            refreshEntryDetails(entry);
            refreshProjectViews();
            status.setText("Saved in memory: " + entry.path());
        } catch (Exception e) {
            showError("Save failed", e.getMessage());
        }
    }

    private void saveAllDirty() {
        for (FileTab tab : openTabs.values()) {
            if (tab.isDirty() && tab.getEditor().isEditable()) {
                try {
                    if (tab.isJavaMode()) {
                        editorSession.applyJavaText(tab.getEntry(), tab.getEditor().getText());
                    } else {
                        editorSession.applyText(tab.getEntry(), tab.getEditor().getText(), null);
                    }
                    tab.setDirty(false);
                } catch (Exception e) {
                    showError("Save failed for " + tab.getEntry().path(), e.getMessage());
                }
            }
        }
        refreshProjectViews();
    }

    private void saveAsJar() {
        if (project == null) {
            showInfo("No project", "Open a file first.");
            return;
        }
        boolean anyDirty = openTabs.values().stream().anyMatch(FileTab::isDirty);
        if (anyDirty && !askYesNo("Save current editor changes before exporting?")) {
            return;
        }
        if (anyDirty) {
            saveAllDirty();
        }
        
        boolean isApk = project.extractedDir().isPresent();
        FileChooser chooser = new FileChooser();
        chooser.setTitle(isApk ? "Build / Export APK" : "Build / Export JAR");
        chooser.getExtensionFilters().add(new ExtensionFilter(isApk ? "APK" : "JAR", isApk ? "*.apk" : "*.jar"));
        chooser.setInitialFileName(defaultOutputName());
        File output = chooser.showSaveDialog(stage);
        if (output == null) {
            return;
        }
        runTask(isApk ? "Building APK..." : "Building JAR...", new Task<Path>() {
            @Override
            protected Path call() throws Exception {
                if (isApk) {
                    projectService.saveApk(project, output.toPath(), line -> javafx.application.Platform.runLater(() -> status.setText(line)));
                } else {
                    projectService.saveAsJar(project, output.toPath());
                }
                return output.toPath();
            }
        }, path -> status.setText("Built successfully to " + path));
    }

    private void exportProject() {
        if (project == null) {
            showInfo("No project", "Open a jar first.");
            return;
        }
        boolean anyDirty = openTabs.values().stream().anyMatch(FileTab::isDirty);
        if (anyDirty) {
            saveAllDirty();
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

    private void showEntryAndJumpToLine(String path, int line) {
        showEntry(path);
        FileTab existingTab = openTabs.get(path);
        if (existingTab != null) {
            if (line > 0) {
                int zeroIndex = line - 1;
                if (existingTab.getEditor().getParagraphs().size() > zeroIndex) {
                    existingTab.getEditor().moveTo(zeroIndex, 0);
                    existingTab.getEditor().requestFollowCaret();
                } else {
                    existingTab.setPendingJumpLine(line);
                }
            }
        }
    }

    private void showEntry(String path) {
        if (project == null || path == null || path.isBlank()) {
            return;
        }
        

        
        FileTab existingTab = openTabs.get(path);
        if (existingTab != null) {
            fileTabs.getSelectionModel().select(existingTab.getTab());
            return;
        }
        project.find(path).ifPresent(entry -> {
            FileTab newTab = new FileTab(entry);
            newTab.getTab().setUserData(newTab);
            newTab.getTab().setOnClosed(e -> openTabs.remove(path));
            
            newTab.getLanguageChoice().getSelectionModel().selectedItemProperty().addListener((obs, old, val) -> {
                if (val != null) {
                    boolean isJava = "Java".equals(val);
                    newTab.setJavaMode(isJava);
                    reloadFileTabContent(newTab);
                }
            });

            fileTabs.getTabs().add(newTab.getTab());
            openTabs.put(path, newTab);
            fileTabs.getSelectionModel().select(newTab.getTab());
            
            configureEditor(newTab);
            reloadFileTabContent(newTab);
        });
    }

    private void reloadFileTabContent(FileTab fileTab) {
        JarEntryData entry = fileTab.getEntry();
        fileTab.getEditor().setEditable(false);
        fileTab.getEditor().replaceText(0, fileTab.getEditor().getLength(), "Loading " + entry.path() + "...");
        status.setText("Loading " + entry.path());

        Task<EntryViewData> task = new Task<>() {
            @Override
            protected EntryViewData call() {
                updateMessage("Loading " + entry.path());
                return buildEntryView(entry, fileTab.isJavaMode());
            }
        };

        task.setOnSucceeded(event -> {
            EntryViewData view = task.getValue();
            fileTab.getEditor().replaceText(0, fileTab.getEditor().getLength(), view.editorText());
            fileTab.getEditor().setEditable(view.editable());
            fileTab.setDirty(false);
            if (view.highlight()) {
                refreshHighlight(fileTab);
            }
            if (getActiveTab() == fileTab) {
                details.setText(view.details().text());
                constantPoolTable.getItems().setAll(view.details().constantPool());
            }
            if (fileTab.getPendingJumpLine() != null) {
                int zeroIndex = fileTab.getPendingJumpLine() - 1;
                if (zeroIndex >= 0 && zeroIndex < fileTab.getEditor().getParagraphs().size()) {
                    fileTab.getEditor().moveTo(zeroIndex, 0);
                    fileTab.getEditor().requestFollowCaret();
                }
                fileTab.setPendingJumpLine(null);
            }
            status.setText(view.statusText());
        });

        task.setOnFailed(event -> {
            fileTab.getEditor().replaceText(0, fileTab.getEditor().getLength(), "Load error: " + task.getException().getMessage());
            status.setText("Failed to load " + entry.path());
        });

        Thread thread = new Thread(task, "jarbe-entry-loader");
        thread.setDaemon(true);
        thread.start();
    }

    private void configureEditor(FileTab fileTab) {
        CodeArea editor = fileTab.getEditor();
        editor.setStyle(editorFontStyle());
        editor.multiPlainChanges()
                .successionEnds(Duration.ofMillis(120))
                .subscribe(ignore -> refreshHighlight(fileTab));
    }

    private void refreshEntryDetails(JarEntryData entry) {
        EntryDetails entryDetails = buildEntryDetails(entry);
        details.setText(entryDetails.text());
        constantPoolTable.getItems().setAll(entryDetails.constantPool());
    }

    private EntryViewData buildEntryView(JarEntryData entry, boolean javaMode) {
        boolean editable = false;
        boolean highlight = false;
        String editorText;
        if (entry.type() == EntryType.CLASS) {
            if (entry.byteSize() > MAX_CLASS_EDITOR_BYTES) {
                editorText = largeEntryInfo(entry, "Class is too large for safe inline disassembly.");
            } else {
                try {
                    editorText = javaMode ? editorSession.javaEditableText(entry) : editorSession.editableText(entry);
                    if (editorText.length() > MAX_EDITOR_CHARS) {
                        editable = false;
                        editorText = largeEntryInfo(entry, "Disassembled text is too large for the inline editor.");
                    } else {
                        editable = true;
                        highlight = !javaMode && editorText.length() <= MAX_HIGHLIGHT_CHARS;
                    }
                } catch (Exception e) {
                    editorText = "Error decompiling class:\n" + e.getMessage();
                }
            }
        } else if (entry.type() == EntryType.TEXT_RESOURCE) {
            if (entry.byteSize() > MAX_TEXT_EDITOR_BYTES) {
                editorText = largeEntryInfo(entry, "Text resource is too large for safe inline editing.")
                        + System.lineSeparator()
                        + previewSample(entry);
            } else {
                editorText = editorSession.editableText(entry);
                if (editorText.length() > MAX_EDITOR_CHARS) {
                    editorText = largeEntryInfo(entry, "Text resource is too large for the inline editor.");
                } else {
                    editable = true;
                    highlight = false;
                }
            }
        } else {
            editorText = lightweightInfo(entry);
        }
        EntryDetails entryDetails = buildEntryDetails(entry);
        String statusText = editable ? "Selected " + entry.path() : "Selected " + entry.path() + " (safe preview)";
        return new EntryViewData(entry, editorText, editable, highlight, entryDetails, statusText);
    }

    private EntryDetails buildEntryDetails(JarEntryData entry) {
        List<ConstantPoolEntry> pool = new ArrayList<>();
        StringBuilder text = new StringBuilder(detailsInfo(entry)).append(System.lineSeparator());
        if (entry.type() == EntryType.CLASS) {
            if (entry.byteSize() > MAX_CLASS_DETAILS_BYTES) {
                text.append(System.lineSeparator())
                        .append("Class analysis skipped: entry is larger than ")
                        .append(HumanSize.format(MAX_CLASS_DETAILS_BYTES))
                        .append(".")
                        .append(System.lineSeparator());
            } else {
                try {
                    byte[] bytes = entry.bytes();
                    ClassSummary summary = analyzer.analyze(bytes);
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
                    pool = constantPoolParser.parse(bytes);
                } catch (RuntimeException e) {
                    text.append(System.lineSeparator()).append("ASM parse error: ").append(e.getMessage()).append(System.lineSeparator());
                }
            }
        }
        return new EntryDetails(text.toString(), pool);
    }

    private void refreshHighlight(FileTab fileTab) {
        CodeArea editor = fileTab.getEditor();
        String text = editor.getText() == null ? "" : editor.getText();
        if (!highlightingEnabled || text.length() > MAX_HIGHLIGHT_CHARS) {
            editor.setStyleSpans(0, plainHighlighting(text.length()));
            return;
        }
        editor.setStyleSpans(0, computeHighlighting(text, fileTab.isJavaMode()));
    }

    private void refreshProjectViews() {
        if (project == null) {
            return;
        }
        if (projectViewsTask != null && projectViewsTask.isRunning()) {
            projectViewsTask.cancel(true);
        }
        JarProject currentProject = project;
        stats.setText("Calculating...");
        callGraph.setText("Calculating...");
        diffResults.setText("Calculating...");
        Task<ProjectViewData> task = new Task<>() {
            @Override
            protected ProjectViewData call() {
                return buildProjectViewData(currentProject);
            }
        };
        projectViewsTask = task;
        task.setOnSucceeded(event -> {
            if (task != projectViewsTask || project != currentProject) {
                return;
            }
            ProjectViewData view = task.getValue();
            stats.setText(view.statsText());
            callGraph.setText(view.callGraphText());
            diffResults.setText(view.diffText());
        });
        task.setOnFailed(event -> {
            if (task != projectViewsTask || project != currentProject) {
                return;
            }
            Throwable error = task.getException();
            String message = "Analysis failed: " + (error == null ? "Unknown error" : error.getMessage());
            stats.setText(message);
            callGraph.setText(message);
            diffResults.setText(currentDiffText(currentProject));
        });
        Thread thread = new Thread(task, "jarbe-project-views");
        thread.setDaemon(true);
        thread.start();
    }

    private void buildTree() {
        if (project == null) {
            tree.setRoot(new EntryTreeItem("No project", null));
            return;
        }
        if (treeBuildTask != null && treeBuildTask.isRunning()) {
            treeBuildTask.cancel(true);
        }
        JarProject currentProject = project;
        String filter = sidebarSearch.getText() == null ? "" : sidebarSearch.getText().strip().toLowerCase(Locale.ROOT);
        Task<EntryTreeItem> task = new Task<>() {
            @Override
            protected EntryTreeItem call() {
                return createTreeRoot(currentProject, filter, this::isCancelled);
            }
        };
        treeBuildTask = task;
        if (tree.getRoot() == null || tree.getRoot().getValue() == null || tree.getRoot().getValue().equals("No project")) {
            tree.setRoot(new EntryTreeItem("Loading tree...", null));
        }
        task.setOnSucceeded(event -> {
            if (task != treeBuildTask || project != currentProject) {
                return;
            }
            tree.setRoot(task.getValue());
        });
        task.setOnFailed(event -> {
            if (task != treeBuildTask || project != currentProject) {
                return;
            }
            tree.setRoot(new EntryTreeItem("Tree failed", null));
        });
        Thread thread = new Thread(task, "jarbe-tree-builder");
        thread.setDaemon(true);
        thread.start();
    }

    private static EntryTreeItem createTreeRoot(JarProject project, String filter, BooleanSupplier cancelled) {
        List<JarEntryData> classEntries = project.classEntries();
        List<JarEntryData> resourceEntries = project.resourceEntries();
        EntryTreeItem root = new EntryTreeItem(project.displayName(), null);
        EntryTreeItem classes = new EntryTreeItem("classes (" + classEntries.size() + ")", "classes/");
        EntryTreeItem files = new EntryTreeItem("files (" + resourceEntries.size() + ")", "files/");
        root.getChildren().add(classes);
        root.getChildren().add(files);

        int shownClasses = addGroupedEntries(classes, "classes", classEntries, filter, cancelled);
        int shownFiles = addGroupedEntries(files, "files", resourceEntries, filter, cancelled);
        if (shownClasses >= MAX_TREE_LEAVES_PER_GROUP) {
            classes.getChildren().add(new EntryTreeItem("More entries hidden. Use search...", null));
        }
        if (shownFiles >= MAX_TREE_LEAVES_PER_GROUP) {
            files.getChildren().add(new EntryTreeItem("More entries hidden. Use search...", null));
        }

        root.setExpanded(true);
        classes.setExpanded(!filter.isBlank());
        files.setExpanded(!filter.isBlank());
        return root;
    }

    private static int addGroupedEntries(EntryTreeItem group, String groupKey, List<JarEntryData> entries,
                                         String filter, BooleanSupplier cancelled) {
        Map<String, EntryTreeItem> nodes = new HashMap<>();
        nodes.put(groupKey, group);
        int shown = 0;
        for (JarEntryData entry : entries) {
            if (cancelled.getAsBoolean() || shown >= MAX_TREE_LEAVES_PER_GROUP) {
                return shown;
            }
            if (!filter.isBlank() && !entry.path().toLowerCase(Locale.ROOT).contains(filter)) {
                byte[] bytes = entry.readBytesOnce();
                if (bytes == null || !new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT).contains(filter)) {
                    continue;
                }
            }
            String[] parts = entry.path().split("/");
            String parentPath = groupKey;
            EntryTreeItem parent = group;
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].isBlank()) {
                    continue;
                }
                String currentPath = parentPath + "/" + parts[i];
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
            shown++;
        }
        return shown;
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
            String text = query.getText();
            SearchType searchType = type.getValue();
            runTask("Searching project", new Task<List<SearchResult>>() {
                @Override
                protected List<SearchResult> call() {
                    return searchEngine.search(project, text, searchType);
                }
            }, results -> {
                if (results.isEmpty()) {
                    searchResults.getItems().clear();
                    showInfo("Search", "No results found.");
                } else {
                    searchResults.getItems().setAll(results);

                    // Popup secondary GUI modal
                    Dialog<Void> resultDialog = new Dialog<>();
                    resultDialog.initOwner(stage);
                    resultDialog.initModality(javafx.stage.Modality.NONE); // Non-modal so they can click around
                    resultDialog.setTitle("Search Results (" + results.size() + ")");
                    resultDialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

                    ListView<SearchResult> listView = new ListView<>();
                    listView.getItems().setAll(results);
                    listView.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
                        @Override
                        protected void updateItem(SearchResult item, boolean empty) {
                            super.updateItem(item, empty);
                            if (empty || item == null) {
                                setText(null);
                            } else {
                                setText(item.compact());
                            }
                        }
                    });

                    listView.setOnMouseClicked(e -> {
                        if (e.getClickCount() == 2) {
                            SearchResult selected = listView.getSelectionModel().getSelectedItem();
                            if (selected != null) {
                                showEntryAndJumpToLine(selected.path(), selected.line());
                                // Do not close the dialog so they can keep exploring results!
                            }
                        }
                    });

                    listView.setPrefSize(700, 400);
                    resultDialog.getDialogPane().setContent(listView);
                    resultDialog.show();
                }
                status.setText("Search results: " + results.size());
            });
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
        ComboBox<String> targetCombo = new ComboBox<>();
        targetCombo.getItems().addAll("Original", "8", "11", "17", "21", "25");
        targetCombo.getSelectionModel().selectFirst();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.addRow(0, new Label("Find"), find);
        grid.addRow(1, new Label("Replace"), replacement);
        grid.addRow(2, new Label("Target Java"), targetCombo);
        dialog.getDialogPane().setContent(grid);
        ButtonType preview = new ButtonType("Preview");
        ButtonType apply = new ButtonType("Apply");
        dialog.getDialogPane().getButtonTypes().addAll(preview, apply, ButtonType.CANCEL);
        dialog.showAndWait().ifPresent(button -> {
            if (find.getText() == null || find.getText().isEmpty()) {
                showError("Replace String", "Find text must not be empty");
                return;
            }
            String findText = find.getText();
            String replacementText = replacement.getText();
            if (button == preview) {
                runTask("Previewing matches", new Task<List<SearchResult>>() {
                    @Override
                    protected List<SearchResult> call() {
                        return searchEngine.search(project, findText, SearchType.STRING);
                    }
                }, results -> {
                    if (results.isEmpty()) {
                        searchResults.getItems().clear();
                        showInfo("Replace Preview", "No matches found.");
                    } else {
                        searchResults.getItems().setAll(results);

                        // Popup secondary GUI modal
                        Dialog<Void> resultDialog = new Dialog<>();
                        resultDialog.initOwner(stage);
                        resultDialog.initModality(javafx.stage.Modality.NONE); // Non-modal so they can click around
                        resultDialog.setTitle("Preview Matches (" + results.size() + ")");
                        resultDialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

                        ListView<SearchResult> listView = new ListView<>();
                        listView.getItems().setAll(results);
                        listView.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
                            @Override
                            protected void updateItem(SearchResult item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || item == null) {
                                    setText(null);
                                } else {
                                    setText(item.compact());
                                }
                            }
                        });

                        listView.setOnMouseClicked(e -> {
                            if (e.getClickCount() == 2) {
                                SearchResult selected = listView.getSelectionModel().getSelectedItem();
                                if (selected != null) {
                                    showEntryAndJumpToLine(selected.path(), selected.line());
                                }
                            }
                        });

                        listView.setPrefSize(700, 400);
                        resultDialog.getDialogPane().setContent(listView);
                        resultDialog.show();
                    }
                    status.setText("Preview matches: " + results.size());
                });
            } else if (button == apply) {
                String selected = targetCombo.getValue();
                Integer targetVersion = ("Original".equals(selected) || selected == null) ? null : Integer.parseInt(selected);
                runTask("Replacing strings", new Task<Integer>() {
                    @Override
                    protected Integer call() {
                        return projectService.replaceString(project, findText, replacementText, targetVersion);
                    }
                }, changed -> {
                    refreshProjectViews();
                    if (currentEntry != null) {
                        showEntry(currentEntry.path());
                    }
                    status.setText("Replace applied to entries: " + changed);
                });
            }
        });
    }

    private void showTranslateDialog() {
        if (project == null) {
            showInfo("No project", "Open a jar first.");
            return;
        }
        if (!confirmDiscardIfDirty()) {
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Translate Project");
        ComboBox<LanguageOption> source = new ComboBox<>();
        source.getItems().setAll(SOURCE_LANGUAGES);
        source.getSelectionModel().select(findLanguage(SOURCE_LANGUAGES, "en"));
        ComboBox<LanguageOption> targetLanguage = new ComboBox<>();
        targetLanguage.getItems().setAll(TARGET_LANGUAGES);
        targetLanguage.getSelectionModel().select(findLanguage(TARGET_LANGUAGES, "vi"));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.addRow(0, new Label("From"), source);
        grid.addRow(1, new Label("To"), targetLanguage);
        dialog.getDialogPane().setContent(grid);
        ButtonType preview = new ButtonType("Preview");
        dialog.getDialogPane().getButtonTypes().addAll(preview, ButtonType.CANCEL);
        dialog.showAndWait().filter(button -> button == preview).ifPresent(button -> {
            LanguageOption sourceLanguage = source.getValue();
            LanguageOption target = targetLanguage.getValue();
            if (sourceLanguage == null || target == null) {
                showError("Translate Project", "Select source and target languages.");
                return;
            }
            runTranslateTask(sourceLanguage, target);
        });
    }

    private void runTranslateTask(LanguageOption sourceLanguage, LanguageOption targetLanguage) {
        Dialog<ButtonType> progressDialog = new Dialog<>();
        progressDialog.setTitle("Translation Progress");
        progressDialog.getDialogPane().getStyleClass().add("translation-progress-dialog");
        TextArea log = readOnlyArea();
        log.getStyleClass().add("translation-log");
        log.setWrapText(true);
        ProgressBar bar = new ProgressBar(0);
        bar.setMaxWidth(Double.MAX_VALUE);
        Label phase = new Label("Preparing translation...");
        VBox content = new VBox(10, phase, bar, log);
        content.setPrefSize(760, 430);
        VBox.setVgrow(log, Priority.ALWAYS);
        progressDialog.getDialogPane().setContent(content);
        ButtonType cancel = new ButtonType("Cancel");
        progressDialog.getDialogPane().getButtonTypes().add(cancel);

        Task<List<TranslationCandidate>> task = new Task<>() {
            @Override
            protected List<TranslationCandidate> call() throws Exception {
                updateMessage("Scanning project...");
                return new TranslationEngine(new GoogleTranslateClient())
                        .preview(project, sourceLanguage.code(), targetLanguage.code(), new TranslationEngine.TranslationProgress() {
                            @Override
                            public void log(String message) {
                                updateMessage(message);
                            }

                            @Override
                            public void update(int completed, int total) {
                                updateProgress(completed, total);
                            }
                        });
            }
        };

        phase.textProperty().bind(task.messageProperty());
        bar.progressProperty().bind(task.progressProperty());
        task.messageProperty().addListener((obs, old, message) -> {
            if (message != null && !message.isBlank()) {
                log.appendText(message + System.lineSeparator());
            }
        });
        progressDialog.setOnCloseRequest(event -> {
            if (task.isRunning()) {
                task.cancel(true);
            }
        });
        progressDialog.setResultConverter(button -> {
            if (button == cancel && task.isRunning()) {
                task.cancel(true);
                status.setText("Translation cancelled");
            }
            return button;
        });

        status.setText("Translating " + sourceLanguage.name() + " to " + targetLanguage.name());
        progress.setVisible(true);
        task.setOnSucceeded(event -> {
            progress.setVisible(false);
            progressDialog.close();
            showTranslationPreview(task.getValue());
        });
        task.setOnCancelled(event -> {
            progress.setVisible(false);
            progressDialog.close();
            status.setText("Translation cancelled");
        });
        task.setOnFailed(event -> {
            progress.setVisible(false);
            progressDialog.close();
            Throwable error = task.getException();
            showError("Translation failed", error == null ? "Unknown error" : error.getMessage());
            status.setText("Translation failed");
        });

        Thread thread = new Thread(task, "jarbe-translate");
        thread.setDaemon(true);
        thread.start();
        progressDialog.show();
    }

    private void showTranslationPreview(List<TranslationCandidate> candidates) {
        if (candidates.isEmpty()) {
            showInfo("Translate Project", "No translatable text was found, or Google returned no changes.");
            status.setText("Translate: no changes");
            return;
        }

        TableView<TranslationPreviewRow> table = new TableView<>();
        table.setEditable(true);
        table.getStyleClass().add("translation-preview-table");
        table.getItems().setAll(candidates.stream().map(TranslationPreviewRow::new).toList());

        TableColumn<TranslationPreviewRow, Boolean> use = new TableColumn<>("Use");
        use.setCellValueFactory(row -> row.getValue().selectedProperty());
        use.setCellFactory(CheckBoxTableCell.forTableColumn(use));
        use.setPrefWidth(60);

        TableColumn<TranslationPreviewRow, String> path = new TableColumn<>("File");
        path.setCellValueFactory(row -> row.getValue().pathProperty());
        path.setPrefWidth(240);

        TableColumn<TranslationPreviewRow, String> location = new TableColumn<>("Location");
        location.setCellValueFactory(row -> row.getValue().locationProperty());
        location.setPrefWidth(120);

        TableColumn<TranslationPreviewRow, String> original = new TableColumn<>("Original");
        original.setCellValueFactory(row -> row.getValue().originalProperty());
        original.setPrefWidth(300);

        TableColumn<TranslationPreviewRow, String> translated = new TableColumn<>("Translated");
        translated.setCellValueFactory(row -> row.getValue().translatedProperty());
        translated.setPrefWidth(340);

        table.getColumns().add(use);
        table.getColumns().add(path);
        table.getColumns().add(location);
        table.getColumns().add(original);
        table.getColumns().add(translated);

        CheckBox selectAll = new CheckBox("Select all");
        selectAll.setSelected(true);
        selectAll.selectedProperty().addListener((obs, old, selected) ->
                table.getItems().forEach(row -> row.selectedProperty().set(selected)));
        Label summary = new Label("Changes: " + candidates.size());
        HBox controls = new HBox(12, selectAll, summary);
        controls.setPadding(new Insets(0, 0, 8, 0));
        VBox content = new VBox(controls, table);
        content.setPrefSize(1080, 560);
        VBox.setVgrow(table, Priority.ALWAYS);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Translate Preview");
        ButtonType apply = new ButtonType("Apply Selected");
        dialog.getDialogPane().getButtonTypes().addAll(apply, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait().filter(button -> button == apply).ifPresent(button -> {
            List<TranslationCandidate> selected = table.getItems().stream()
                    .filter(TranslationPreviewRow::selected)
                    .map(TranslationPreviewRow::candidate)
                    .toList();
            if (selected.isEmpty()) {
                status.setText("Translate: nothing selected");
                return;
            }
            int changed = new TranslationEngine(new GoogleTranslateClient()).apply(project, selected);
            refreshProjectViews();
            if (currentEntry != null) {
                String pathToReload = currentEntry.path();
                currentEntry = null;
                showEntry(pathToReload);
            }
            status.setText("Translate applied to entries: " + changed);
            showInfo("Translate Project", "Applied selected translations to " + changed + " entries in memory. Use Save As JAR to export.");
        });
    }

    private static LanguageOption findLanguage(List<LanguageOption> languages, String code) {
        return languages.stream()
                .filter(language -> language.code().equals(code))
                .findFirst()
                .orElse(languages.getFirst());
    }

    private ProjectViewData buildProjectViewData(JarProject source) {
        List<JarEntryData> classEntries = source.classEntries();
        List<JarEntryData> resourceEntries = source.resourceEntries();
        long classBytes = classEntries.stream().mapToLong(JarEntryData::byteSize).sum();
        long resourceBytes = resourceEntries.stream().mapToLong(JarEntryData::byteSize).sum();
        long loadedEntries = source.entries().stream().filter(JarEntryData::loaded).count();
        String diffText = currentDiffText(source);
        String statsText = "Classes: " + classEntries.size() + System.lineSeparator()
                + "Resources: " + resourceEntries.size() + System.lineSeparator()
                + "Class bytes: " + HumanSize.format(classBytes) + System.lineSeparator()
                + "Resource bytes: " + HumanSize.format(resourceBytes) + System.lineSeparator()
                + "Loaded entries: " + loadedEntries + "/" + source.entries().size() + System.lineSeparator()
                + System.lineSeparator()
                + "Safe mode: project-wide class analysis is disabled." + System.lineSeparator()
                + "A class is disassembled only when you select it.";
        String callGraphText = "Call graph is not built automatically in safe mode." + System.lineSeparator()
                + "This prevents loading and parsing every class after opening a large jar.";
        return new ProjectViewData(statsText, callGraphText, diffText);
    }

    private String currentDiffText(JarProject source) {
        List<JarEntryData> modified = source.modifiedEntries();
        if (modified.isEmpty()) {
            return "No differences";
        }
        StringBuilder builder = new StringBuilder("Modified Entries:").append(System.lineSeparator());
        int shown = 0;
        for (JarEntryData entry : modified) {
            if (shown >= 2000) {
                builder.append("  ... ").append(modified.size() - shown).append(" more").append(System.lineSeparator());
                break;
            }
            builder.append("  ").append(entry.type()).append("  ").append(entry.path()).append(System.lineSeparator());
            shown++;
        }
        return builder.toString();
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
        task.setOnCancelled(event -> {
            progress.setVisible(false);
            status.setText("Cancelled: " + message);
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



    private static StyleSpans<Collection<String>> computeHighlighting(String text, boolean isJavaMode) {
        Matcher matcher = (isJavaMode ? JAVA_HIGHLIGHT_PATTERN : JASM_HIGHLIGHT_PATTERN).matcher(text);
        int lastKeywordEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            String styleClass = "jasm-plain";
            if (isJavaMode) {
                styleClass =
                        matcher.group("COMMENT") != null ? "java-comment" :
                        matcher.group("STRING") != null ? "java-string" :
                        matcher.group("KEYWORD") != null ? "java-keyword" :
                        matcher.group("ANNOTATION") != null ? "java-annotation" :
                        matcher.group("NUMBER") != null ? "java-number" :
                        "java-plain";
            } else {
                styleClass =
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
            }
            spansBuilder.add(Collections.singleton("jasm-plain"), matcher.start() - lastKeywordEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKeywordEnd = matcher.end();
        }
        spansBuilder.add(Collections.singleton("jasm-plain"), text.length() - lastKeywordEnd);
        return spansBuilder.create();
    }

    private static StyleSpans<Collection<String>> plainHighlighting(int length) {
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        spansBuilder.add(Collections.singleton("jasm-plain"), Math.max(0, length));
        return spansBuilder.create();
    }

    private static String opcodePattern() {
        return Arrays.stream(Printer.OPCODES)
                .filter(name -> name != null && !name.isBlank())
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
    }

    private String lightweightInfo(JarEntryData entry) {
        StringBuilder builder = new StringBuilder();
        builder.append("Path: ").append(entry.path()).append(System.lineSeparator())
                .append("Type: ").append(entry.type()).append(System.lineSeparator())
                .append("Size: ").append(HumanSize.format(entry.byteSize())).append(System.lineSeparator())
                .append("Loaded: ").append(entry.loaded()).append(System.lineSeparator())
                .append("Modified: ").append(entry.modified()).append(System.lineSeparator());
        if (FileTypeUtil.isBinaryPreviewPath(entry.path())) {
            builder.append("Binary viewer: metadata only").append(System.lineSeparator());
        }
        return builder.toString();
    }

    private String detailsInfo(JarEntryData entry) {
        StringBuilder builder = new StringBuilder();
        builder.append("Path: ").append(entry.path()).append(System.lineSeparator())
                .append("Type: ").append(entry.type()).append(System.lineSeparator())
                .append("Size: ").append(HumanSize.format(entry.byteSize())).append(System.lineSeparator())
                .append("Loaded: ").append(entry.loaded()).append(System.lineSeparator())
                .append("SHA1: ").append(sha1Display(entry)).append(System.lineSeparator())
                .append("Modified: ").append(entry.modified()).append(System.lineSeparator());
        if (FileTypeUtil.isBinaryPreviewPath(entry.path())) {
            builder.append("Binary viewer: metadata only").append(System.lineSeparator());
        }
        return builder.toString();
    }

    private String sha1Display(JarEntryData entry) {
        if (!entry.loaded() && entry.byteSize() > MAX_HASH_BYTES) {
            return "deferred for large entry";
        }
        try {
            return HashUtil.sha1(entry.bytes());
        } catch (RuntimeException e) {
            return "unavailable: " + e.getMessage();
        }
    }

    private String largeEntryInfo(JarEntryData entry, String reason) {
        return lightweightInfo(entry)
                + System.lineSeparator()
                + reason + System.lineSeparator()
                + "Inline editor disabled to keep the GUI responsive." + System.lineSeparator()
                + "Use Export Project if you need the full file content.";
    }

    private String previewSample(JarEntryData entry) {
        byte[] sample = entry.sampleBytes();
        if (sample.length == 0) {
            return "Sample: empty";
        }
        String preview = new String(sample, StandardCharsets.UTF_8)
                .replace("\0", "\\0");
        if (sample.length < entry.byteSize()) {
            preview += System.lineSeparator() + "... sample only, full entry is "
                    + HumanSize.format(entry.byteSize()) + ".";
        }
        return "Sample:" + System.lineSeparator() + preview;
    }

    private String defaultOutputName() {
        if (project == null) return "project-edited.jar";
        return project.sourcePath()
                .map(p -> p.getFileName().toString())
                .map(name -> name.replaceFirst("\\.(jar|zip|apk)$", "-edited.$1"))
                .orElse("project-edited.jar");
    }

    private record EntryDetails(String text, List<ConstantPoolEntry> constantPool) {
    }

    private record EntryViewData(JarEntryData entry, String editorText, boolean editable, boolean highlight,
                                 EntryDetails details, String statusText) {
    }

    private record ProjectViewData(String statsText, String callGraphText, String diffText) {
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
