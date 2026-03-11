package br.com.netto.gastos.ui;

import br.com.netto.gastos.service.AuthService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class LoginView {

    private final Stage stage;
    private final AuthService auth = new AuthService();

    private final BorderPane root = new BorderPane();

    public LoginView(Stage stage) {
        this.stage = stage;
        build();
    }

    public Parent getRoot() {
        return root;
    }

    private void build() {
        Label title = new Label("Controle de Gastos");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: bold;");

        Label subtitle = new Label("Login com Supabase (PostgreSQL) — JavaFX");
        subtitle.setStyle("-fx-opacity: 0.75;");

        VBox header = new VBox(6, title, subtitle);
        header.setPadding(new Insets(30, 30, 10, 30));

        TextField email = new TextField();
        email.setPromptText("Email");

        PasswordField password = new PasswordField();
        password.setPromptText("Senha");

        Button btnLogin = new Button("Entrar");
        Button btnSignup = new Button("Criar conta");
        btnLogin.setDefaultButton(true);

        Label status = new Label();
        status.setWrapText(true);
        status.setMaxWidth(Double.MAX_VALUE);
        status.setStyle("-fx-text-fill: #b00020;");

        HBox actions = new HBox(10, btnLogin, btnSignup);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox form = new VBox(12,
                new Label("Acesso"),
                email,
                password,
                actions,
                status
        );
        form.setPadding(new Insets(30));
        form.setMaxWidth(720);
        form.setStyle("""
                -fx-background-color: white;
                -fx-background-radius: 12;
                -fx-border-color: #e5e5e5;
                -fx-border-radius: 12;
                -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 14, 0, 0, 4);
                """);

        StackPane center = new StackPane(form);
        center.setPadding(new Insets(0, 30, 30, 30));
        center.setStyle("-fx-background-color: #f6f7fb;");

        root.setTop(header);
        root.setCenter(center);

        btnLogin.setOnAction(e -> {
            status.setText("");
            try {
                String em = email.getText().trim();
                String pw = password.getText().trim();
                if (em.isBlank() || pw.isBlank()) {
                    status.setText("Preencha email e senha.");
                    return;
                }
                auth.signIn(em, pw);

                DashboardView dash = new DashboardView(stage);
                stage.setScene(new Scene(dash.getRoot(), 1100, 720));

            } catch (Exception ex) {
                status.setText("Falha no login: " + ex.getMessage());
            }
        });

        btnSignup.setOnAction(e -> {
            status.setText("");
            try {
                String em = email.getText().trim();
                String pw = password.getText().trim();
                if (em.isBlank() || pw.isBlank()) {
                    status.setText("Preencha email e senha.");
                    return;
                }
                auth.signUp(em, pw);
                status.setStyle("-fx-text-fill: #0b6b2b;");
                status.setText("Conta criada. confirme a verificação enviada para o email: " + em);
            } catch (Exception ex) {
                status.setStyle("-fx-text-fill: #b00020;");
                status.setText("Falha ao criar conta: " + ex.getMessage());
            }
        });
    }
}