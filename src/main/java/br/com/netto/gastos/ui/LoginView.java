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
        title.setStyle("-fx-font-size: 30px; -fx-font-weight: 800; -fx-text-fill: #172033;");

        Label subtitle = new Label("Login - AL: LUCAS NETTO - RU: 4250816");
        subtitle.setStyle("-fx-text-fill: #667085;");

        VBox header = new VBox(6, title, subtitle);
        header.setPadding(new Insets(30, 30, 10, 30));

        TextField email = new TextField();
        email.setPromptText("Email");

        PasswordField password = new PasswordField();
        password.setPromptText("Senha");

        Button btnLogin = new Button("Entrar");
        Button btnSignup = new Button("Criar conta");
        Button rmbPass = new Button("Esqueci a senha");
        btnLogin.setStyle("-fx-background-color: #2f6fed; -fx-text-fill: white;");
        btnSignup.setStyle("-fx-background-color: #0f9f8f; -fx-text-fill: white;");
        rmbPass.setStyle("-fx-background-color: #e9eef5; -fx-text-fill: #172033;");
        btnLogin.setDefaultButton(true);

        Label status = new Label();
        status.setWrapText(true);
        status.setMaxWidth(Double.MAX_VALUE);
        status.setStyle("-fx-text-fill: #b00020;");

        HBox actions = new HBox(10, btnLogin, btnSignup, rmbPass);
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
                -fx-background-radius: 8;
                -fx-border-color: #dbe3ee;
                -fx-border-radius: 8;
                -fx-effect: dropshadow(three-pass-box, rgba(15,23,42,0.08), 22, 0, 0, 8);
                """);

        StackPane center = new StackPane(form);
        center.setPadding(new Insets(0, 30, 30, 30));
        center.setStyle("-fx-background-color: #f4f7fb;");

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
                Scene scene = new Scene(dash.getRoot(), 1100, 720);
                UiTheme.apply(scene);
                stage.setScene(scene);

            } catch (Exception ex) {
                status.setText("Falha no login: " + ex.getMessage());
            }
        });

        btnSignup.setOnAction(e -> {
            SignupView signupView = new SignupView(stage, email.getText().trim());
            Scene scene = new Scene(signupView.getRoot(), 980, 640);
            UiTheme.apply(scene);
            stage.setScene(scene);
        });

        rmbPass.setOnAction(e -> {
            status.setText("");
            try {
                String em = email.getText().trim();
                if (em.isBlank()) {
                    status.setStyle("-fx-text-fill: #b00020;");
                    status.setText("Informe o email para recuperar a senha.");
                    return;
                }

                auth.resetPassword(em);
                status.setStyle("-fx-text-fill: #0b6b2b;");
                status.setText("Enviamos um link de redefinicao para o email: " + em);
            } catch (Exception ex) {
                status.setStyle("-fx-text-fill: #b00020;");
                status.setText(ex.getMessage());
            }
        });

    }
}
