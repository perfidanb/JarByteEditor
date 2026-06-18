package vn.perfidanb.jarbe.gui;

import javafx.scene.Node;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Locale;

final class FileIconFactory {
    private FileIconFactory() {
    }

    static Node forTreeEntry(String path, String name) {
        if (path == null) {
            return icon(Feather.PACKAGE, "jar-icon", 16);
        }
        if (path.equals("classes/")) {
            return icon(Feather.FOLDER, "classes-group-icon", 16);
        }
        if (path.equals("files/")) {
            return icon(Feather.FILE_TEXT, "files-group-icon", 16);
        }
        if (path.endsWith("/")) {
            return icon(Feather.FOLDER, "folder-icon", 16);
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".class")) {
            return icon(Feather.CODE, "class-icon", 16);
        }
        if (lower.endsWith(".yml") || lower.endsWith(".yaml") || lower.endsWith(".properties")) {
            return icon(Feather.SLIDERS, "config-icon", 16);
        }
        if (lower.endsWith(".json")) {
            return icon(Feather.FILE_TEXT, "json-icon", 16);
        }
        if (lower.endsWith(".xml")) {
            return icon(Feather.CODE, "xml-icon", 16);
        }
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".webp")) {
            return icon(Feather.IMAGE, "image-icon", 16);
        }
        if (lower.endsWith(".jar") || lower.endsWith(".zip")) {
            return icon(Feather.ARCHIVE, "archive-icon", 16);
        }
        if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".csv")) {
            return icon(Feather.FILE_TEXT, "text-icon", 16);
        }
        if (name != null && name.contains(".")) {
            return icon(Feather.FILE, "resource-icon", 16);
        }
        return icon(Feather.BOX, "binary-icon", 16);
    }

    static Node actionIcon(String styleClass) {
        Feather ikon = switch (styleClass) {
            case "open-action-icon" -> Feather.FOLDER_PLUS;
            case "save-action-icon" -> Feather.SAVE;
            case "archive-action-icon" -> Feather.ARCHIVE;
            case "export-action-icon" -> Feather.UPLOAD;
            case "find-action-icon" -> Feather.SEARCH;
            case "replace-action-icon" -> Feather.EDIT_3;
            case "translate-action-icon" -> Feather.GLOBE;
            default -> Feather.CIRCLE;
        };
        return icon(ikon, styleClass, 15);
    }

    private static Node icon(Feather ikon, String styleClass, int size) {
        FontIcon icon = new FontIcon(ikon);
        icon.setIconSize(size);
        icon.getStyleClass().addAll("app-icon", styleClass);
        return icon;
    }
}
