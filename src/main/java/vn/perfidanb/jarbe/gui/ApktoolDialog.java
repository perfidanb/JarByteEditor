package vn.perfidanb.jarbe.gui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import vn.perfidanb.jarbe.service.ApktoolService;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class ApktoolDialog extends Dialog<Void> {
    private final Stage parentStage;
    private final TextArea console;

    public ApktoolDialog(Stage parentStage) {
        this.parentStage = parentStage;
        setTitle("Apktool Manager");
        setResizable(true);

        TabPane tabs = new TabPane();
        tabs.getTabs().add(createDecodeTab());
        tabs.getTabs().add(createBuildTab());
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        console = new TextArea();
        console.setEditable(false);
        console.setWrapText(true);
        console.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px;");
        VBox.setVgrow(console, Priority.ALWAYS);

        VBox content = new VBox(10, tabs, new Label("Console Output:"), console);
        content.setPadding(new Insets(10));
        content.setPrefSize(600, 500);

        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    }

    private Tab createDecodeTab() {
        Tab tab = new Tab("Decode APK");
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        TextField apkField = new TextField();
        Button browseApk = new Button("Browse...");
        browseApk.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("APK Files", "*.apk"));
            File file = fc.showOpenDialog(parentStage);
            if (file != null) apkField.setText(file.getAbsolutePath());
        });

        TextField outField = new TextField();
        Button browseOut = new Button("Browse...");
        browseOut.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            File dir = dc.showDialog(parentStage);
            if (dir != null) outField.setText(dir.getAbsolutePath());
        });

        Button runBtn = new Button("Run Decode");
        runBtn.getStyleClass().add("accent");
        runBtn.setOnAction(e -> {
            if (apkField.getText().isBlank() || outField.getText().isBlank()) {
                appendLog("Error: Please select both APK and output directory.\n");
                return;
            }
            runApktoolCommand(() -> ApktoolService.decode(new File(apkField.getText()), new File(outField.getText())), runBtn);
        });

        grid.addRow(0, new Label("Target APK:"), apkField, browseApk);
        grid.addRow(1, new Label("Output Dir:"), outField, browseOut);
        
        VBox box = new VBox(10, grid, new HBox(runBtn));
        box.setAlignment(Pos.TOP_CENTER);
        tab.setContent(box);
        return tab;
    }

    private Tab createBuildTab() {
        Tab tab = new Tab("Build APK");
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        TextField dirField = new TextField();
        Button browseDir = new Button("Browse...");
        browseDir.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            File dir = dc.showDialog(parentStage);
            if (dir != null) dirField.setText(dir.getAbsolutePath());
        });

        TextField outField = new TextField();
        Button browseOut = new Button("Browse...");
        browseOut.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("APK Files", "*.apk"));
            File file = fc.showSaveDialog(parentStage);
            if (file != null) outField.setText(file.getAbsolutePath());
        });

        Button runBtn = new Button("Run Build");
        runBtn.getStyleClass().add("accent");
        runBtn.setOnAction(e -> {
            if (dirField.getText().isBlank() || outField.getText().isBlank()) {
                appendLog("Error: Please select both input directory and output APK.\n");
                return;
            }
            runApktoolCommand(() -> ApktoolService.build(new File(dirField.getText()), new File(outField.getText())), runBtn);
        });

        grid.addRow(0, new Label("Input Dir:"), dirField, browseDir);
        grid.addRow(1, new Label("Output APK:"), outField, browseOut);

        VBox box = new VBox(10, grid, new HBox(runBtn));
        box.setAlignment(Pos.TOP_CENTER);
        tab.setContent(box);
        return tab;
    }

    private interface CommandProvider {
        ProcessBuilder get() throws Exception;
    }

    private void runApktoolCommand(CommandProvider provider, Button triggerBtn) {
        triggerBtn.setDisable(true);
        console.clear();
        appendLog("Preparing apktool...\n");

        Thread thread = new Thread(() -> {
            try {
                ProcessBuilder pb = provider.get();
                appendLog("Executing: " + String.join(" ", pb.command()) + "\n");
                Process process = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        appendLog(line + "\n");
                    }
                }
                
                int exitCode = process.waitFor();
                appendLog("\nProcess exited with code: " + exitCode + "\n");
            } catch (Exception ex) {
                appendLog("\nError executing apktool: " + ex.getMessage() + "\n");
                ex.printStackTrace();
            } finally {
                Platform.runLater(() -> triggerBtn.setDisable(false));
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void appendLog(String text) {
        Platform.runLater(() -> {
            console.appendText(text);
            console.positionCaret(console.getLength());
        });
    }
}
