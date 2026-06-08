package br.com.netto.gastos.ui;

import javafx.scene.Scene;
import javafx.scene.control.DialogPane;

/**
 * Aplica a folha de estilos compartilhada nas cenas e caixas de dialogo JavaFX.
 */
public final class UiTheme {
    /** Impede a criacao de instancias desta classe utilitaria. */
    private UiTheme() {}

    /** Adiciona a folha de estilos compartilhada ao componente. */
    public static void apply(Scene scene) {
        String css = stylesheet();
        if (css != null && !scene.getStylesheets().contains(css)) {
            scene.getStylesheets().add(css);
        }
    }

    /** Adiciona a folha de estilos compartilhada ao componente. */
    public static void apply(DialogPane pane) {
        String css = stylesheet();
        if (css != null && !pane.getStylesheets().contains(css)) {
            pane.getStylesheets().add(css);
        }
    }

    /** Localiza a folha de estilos JavaFX empacotada nos recursos. */
    private static String stylesheet() {
        var url = UiTheme.class.getResource("/css/app.css");
        return url == null ? null : url.toExternalForm();
    }
}
