package br.com.netto.gastos;

import br.com.netto.gastos.ui.LoginView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) {
        stage.setTitle("Controle de Gastos - Netto");

        LoginView loginView = new LoginView(stage);
        Scene scene = new Scene(loginView.getRoot(), 980, 640);

        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}