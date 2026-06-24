package vn.perfidanb.jarbe.gui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import vn.perfidanb.jarbe.model.EntryType;
import vn.perfidanb.jarbe.model.JarEntryData;

public class FileTab {
    private final JarEntryData entry;
    private final Tab tab;
    private final CodeArea editor;
    private final ComboBox<String> languageChoice;
    private boolean isJavaMode = false;
    private boolean isDirty = false;

    public FileTab(JarEntryData entry) {
        this.entry = entry;
        this.tab = new Tab(entry.path().substring(entry.path().lastIndexOf('/') + 1));
        this.editor = new CodeArea();
        
        editor.setWrapText(false);
        editor.setParagraphGraphicFactory(LineNumberFactory.get(editor));
        editor.getStyleClass().add("bytecode-editor");

        languageChoice = new ComboBox<>();
        languageChoice.getItems().addAll("JASM", "Java");
        languageChoice.getSelectionModel().select("JASM");
        if (entry.type() != EntryType.CLASS) {
            languageChoice.setDisable(true);
        }

        HBox topBar = new HBox(10, new Label("Language:"), languageChoice);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(5));

        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setCenter(new VirtualizedScrollPane<>(editor));

        tab.setContent(root);
        
        editor.textProperty().addListener((obs, old, text) -> {
            if (editor.isEditable()) {
                isDirty = true;
                updateTabTitle();
            }
        });
    }

    public void setJavaMode(boolean javaMode) {
        this.isJavaMode = javaMode;
        languageChoice.getSelectionModel().select(javaMode ? "Java" : "JASM");
    }

    public boolean isJavaMode() {
        return isJavaMode;
    }

    public ComboBox<String> getLanguageChoice() {
        return languageChoice;
    }

    public Tab getTab() {
        return tab;
    }

    public CodeArea getEditor() {
        return editor;
    }

    public JarEntryData getEntry() {
        return entry;
    }

    public boolean isDirty() {
        return isDirty;
    }

    public void setDirty(boolean dirty) {
        this.isDirty = dirty;
        updateTabTitle();
    }

    private void updateTabTitle() {
        String title = entry.path().substring(entry.path().lastIndexOf('/') + 1);
        if (isDirty) {
            title = "*" + title;
        }
        tab.setText(title);
    }
}
