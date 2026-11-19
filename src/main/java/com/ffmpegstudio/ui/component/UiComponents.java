package com.ffmpegstudio.ui.component;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;

import java.util.Optional;

/**
 * Reusable UI component helpers adhering to the FFmpeg Studio Design System.
 */
public final class UiComponents {

    private UiComponents() {}

    public static void copyToClipboard(String text) {
        if (text == null) return;
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    public static boolean confirmAction(String title, String header, String body) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(body);
        Optional<ButtonType> res = alert.showAndWait();
        return res.isPresent() && res.get() == ButtonType.OK;
    }

    public static void showErrorWithDetails(String title, String friendlyMessage, String technicalDetails) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(friendlyMessage);
        if (technicalDetails != null && !technicalDetails.isBlank()) {
            TextArea area = new TextArea(technicalDetails);
            area.setEditable(false);
            area.setWrapText(true);
            area.setPrefRowCount(8);
            alert.getDialogPane().setExpandableContent(area);
        }
        alert.showAndWait();
    }

    public static VBox createEmptyState(String title, String subtitle) {
        VBox box = new VBox(6);
        box.getStyleClass().add("empty-state");
        Label t = new Label(title);
        t.getStyleClass().add("card-title");
        Label s = new Label(subtitle);
        s.getStyleClass().add("text-muted");
        s.setWrapText(true);
        box.getChildren().addAll(t, s);
        return box;
    }
}
