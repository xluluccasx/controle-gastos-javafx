package br.com.netto.gastos;

import br.com.netto.gastos.ui.LoginView;
import br.com.netto.gastos.ui.UiTheme;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Ponto de entrada JavaFX que cria a janela principal e exibe a tela de login.
 */
public class MainApp extends Application {

    /** Configura o palco principal e apresenta a primeira tela. */
    @Override
    public void start(Stage stage) {
        stage.setTitle("Controle de Gastos - Netto");

        LoginView loginView = new LoginView(stage);
        Scene scene = new Scene(loginView.getRoot(), 980, 640);
        UiTheme.apply(scene);

        stage.setScene(scene);
        stage.show();
    }

    /** Inicia a aplicacao. */
    public static void main(String[] args) {
        launch(args);
    }
}
