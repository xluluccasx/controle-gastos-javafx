package br.com.netto.gastos.ui;

import javafx.scene.Scene;
import javafx.scene.control.DialogPane;

public final class UiTheme {
    private UiTheme() {}

    public static void apply(Scene scene) {
        String css = stylesheet();
        if (css != null && !scene.getStylesheets().contains(css)) {
            scene.getStylesheets().add(css);
        }
    }

    public static void apply(DialogPane pane) {
        String css = stylesheet();
        if (css != null && !pane.getStylesheets().contains(css)) {
            pane.getStylesheets().add(css);
        }
    }

    private static String stylesheet() {
        var url = UiTheme.class.getResource("/css/app.css");
        return url == null ? null : url.toExternalForm();
    }
}
