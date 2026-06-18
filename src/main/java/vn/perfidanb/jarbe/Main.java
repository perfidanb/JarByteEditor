package vn.perfidanb.jarbe;

import javafx.application.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import vn.perfidanb.jarbe.cli.JarbeCli;
import vn.perfidanb.jarbe.gui.JarByteEditorApp;

public final class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            log.debug("Launching JavaFX GUI");
            Application.launch(JarByteEditorApp.class, args);
            return;
        }

        CommandLine commandLine = new CommandLine(new JarbeCli());
        commandLine.setExecutionExceptionHandler((ex, cmd, parseResult) -> {
            cmd.getErr().println("Error: " + ex.getMessage());
            return 1;
        });
        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }
}
