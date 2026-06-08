package br.com.netto.gastos.ui;

import br.com.netto.gastos.service.AuthService;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Constroi a tela JavaFX de cadastro e valida a forca da senha.
 */
public class SignupView {

    private static final String ERROR_STYLE = "-fx-text-fill: #b00020;";
    private static final String SUCCESS_STYLE = "-fx-text-fill: #0b6b2b;";

    private final Stage stage;
    private final String initialEmail;
    private final AuthService auth = new AuthService();
    private final BorderPane root = new BorderPane();

    /** Inicializa a tela de cadastro e preenche o email recebido. */
    public SignupView(Stage stage, String initialEmail) {
        this.stage = stage;
        this.initialEmail = initialEmail == null ? "" : initialEmail;
        build();
    }

    /** Retorna o componente raiz que sera exibido na cena. */
    public Parent getRoot() {
        return root;
    }

    /** Monta os componentes e eventos da tela. */
    private void build() {
        Label title = new Label("Controle de Gastos");
        title.setStyle("-fx-font-size: 30px; -fx-font-weight: 800; -fx-text-fill: #172033;");

        Label subtitle = new Label("Criar conta");
        subtitle.setStyle("-fx-text-fill: #667085;");

        VBox header = new VBox(6, title, subtitle);
        header.setPadding(new Insets(30, 30, 10, 30));

        TextField email = new TextField(initialEmail);
        email.setPromptText("Email");

        PasswordField password = new PasswordField();
        password.setPromptText("Senha");

        PasswordField confirmPassword = new PasswordField();
        confirmPassword.setPromptText("Confirmar senha");

        ProgressBar strengthBar = new ProgressBar(0);
        strengthBar.setMaxWidth(Double.MAX_VALUE);

        Label strengthLabel = new Label("Forca da senha: informe uma senha");
        strengthLabel.setStyle("-fx-text-fill: #6b7280;");

        Label status = new Label();
        status.setWrapText(true);
        status.setMaxWidth(Double.MAX_VALUE);
        status.setStyle(ERROR_STYLE);

        Button btnCreate = new Button("Criar conta");
        Button btnBack = new Button("Voltar ao login");
        btnCreate.setStyle("-fx-background-color: #2f6fed; -fx-text-fill: white;");
        btnBack.setStyle("-fx-background-color: #e9eef5; -fx-text-fill: #172033;");
        btnCreate.setDefaultButton(true);

        HBox actions = new HBox(10, btnCreate, btnBack);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox form = new VBox(12,
                new Label("Cadastro"),
                email,
                password,
                confirmPassword,
                strengthBar,
                strengthLabel,
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

        ChangeListener<String> strengthUpdater = (obs, oldValue, newValue) ->
                updateStrength(password.getText(), strengthBar, strengthLabel);
        password.textProperty().addListener(strengthUpdater);

        btnCreate.setOnAction(e -> {
            status.setText("");
            status.setStyle(ERROR_STYLE);

            String em = email.getText().trim();
            String pw = password.getText();
            String confirmation = confirmPassword.getText();

            if (em.isBlank() || pw.isBlank() || confirmation.isBlank()) {
                status.setText("Preencha email, senha e confirmacao.");
                return;
            }

            if (!pw.equals(confirmation)) {
                status.setText("As senhas nao conferem.");
                return;
            }

            PasswordStrength strength = evaluatePassword(pw);
            if (strength.score < 5) {
                status.setText("Use uma senha forte: minimo de 8 caracteres, maiuscula, minuscula, numero e simbolo.");
                return;
            }

            try {
                auth.signUp(em, pw);
                status.setStyle(SUCCESS_STYLE);
                status.setText("Conta criada. Verifique o email enviado para: " + em);
                btnCreate.setDisable(true);
            } catch (Exception ex) {
                status.setStyle(ERROR_STYLE);
                status.setText("Falha ao criar conta: " + ex.getMessage());
            }
        });

        btnBack.setOnAction(e -> showLogin(email.getText().trim()));
    }

    /** Retorna para a cena de login. */
    private void showLogin(String email) {
        LoginView loginView = new LoginView(stage);
        Scene scene = new Scene(loginView.getRoot(), 980, 640);
        UiTheme.apply(scene);
        stage.setScene(scene);
    }

    /** Atualiza a barra e o texto de forca da senha. */
    private void updateStrength(String password, ProgressBar strengthBar, Label strengthLabel) {
        PasswordStrength strength = evaluatePassword(password);
        strengthBar.setProgress(strength.score / 5.0);
        strengthLabel.setText("Forca da senha: " + strength.label);
        strengthLabel.setStyle("-fx-text-fill: " + strength.color + ";");
    }

    /** Pontua a senha conforme tamanho e variedade de caracteres. */
    private PasswordStrength evaluatePassword(String password) {
        if (password == null || password.isBlank()) {
            return new PasswordStrength(0, "informe uma senha", "#6b7280");
        }

        int score = 0;
        if (password.length() >= 8) score++;
        if (password.matches(".*[a-z].*")) score++;
        if (password.matches(".*[A-Z].*")) score++;
        if (password.matches(".*\\d.*")) score++;
        if (password.matches(".*[^A-Za-z0-9].*")) score++;

        if (score <= 2) {
            return new PasswordStrength(score, "Fraca", "#b00020");
        }
        if (score <= 4) {
            return new PasswordStrength(score, "Media", "#b45309");
        }
        return new PasswordStrength(score, "Forte", "#0b6b2b");
    }

    /** Agrupa a pontuacao e a apresentacao visual da forca da senha. */
    private record PasswordStrength(int score, String label, String color) {
    }
}
