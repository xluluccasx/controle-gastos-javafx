package br.com.netto.gastos.ui;

import br.com.netto.gastos.config.Session;
import br.com.netto.gastos.model.Category;
import br.com.netto.gastos.model.Transaction;
import br.com.netto.gastos.model.TxType;
import br.com.netto.gastos.service.AuthService;
import br.com.netto.gastos.service.TransactionService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class DashboardView {

    private final Stage stage;
    private final TransactionService txService = new TransactionService();
    private final AuthService authService = new AuthService();

    private final BorderPane root = new BorderPane();

    private final TableView<Transaction> table = new TableView<>();
    private final ObservableList<Transaction> rows = FXCollections.observableArrayList();

    private final Label lblIncome = new Label("Receitas: R$ 0,00");
    private final Label lblExpense = new Label("Despesas: R$ 0,00");
    private final Label lblBalance = new Label("Saldo: R$ 0,00");

    private final PieChart pieByCategory = new PieChart();
    private final BarChart<String, Number> barType;

    private final ComboBox<YearMonth> monthPicker = new ComboBox<>();

    public DashboardView(Stage stage) {
        this.stage = stage;

        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        barType = new BarChart<>(x, y);
        barType.setTitle("Totais por Tipo");
        x.setLabel("Tipo");
        y.setLabel("R$");

        build();
        loadInitial();
    }

    public Parent getRoot() {
        return root;
    }

    private void build() {
        // Top bar
        Label title = new Label("Dashboard");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        Label user = new Label("Usuário: " + Session.email());
        user.setStyle("-fx-opacity: 0.8;");

        Button btnAdd = new Button("Novo lançamento");
        Button btnDelete = new Button("Excluir selecionado");
        Button btnLogout = new Button("Sair");

        HBox topLeft = new HBox(14, title, user);
        topLeft.setAlignment(Pos.CENTER_LEFT);

        HBox topRight = new HBox(10, btnAdd, btnDelete, btnLogout);
        topRight.setAlignment(Pos.CENTER_RIGHT);

        BorderPane top = new BorderPane();
        top.setLeft(topLeft);
        top.setRight(topRight);
        top.setPadding(new Insets(18));
        top.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e6e6e6; -fx-border-width: 0 0 1 0;");

        // KPI cards
        HBox kpis = new HBox(12, card(lblIncome), card(lblExpense), card(lblBalance));
        kpis.setPadding(new Insets(12, 18, 12, 18));

        // Month picker
        monthPicker.setPrefWidth(220);
        monthPicker.setItems(FXCollections.observableArrayList(
                YearMonth.now(),
                YearMonth.now().minusMonths(1),
                YearMonth.now().minusMonths(2),
                YearMonth.now().minusMonths(3),
                YearMonth.now().minusMonths(4),
                YearMonth.now().minusMonths(5),
                YearMonth.now().minusMonths(6),
                YearMonth.now().minusMonths(7),
                YearMonth.now().minusMonths(8),
                YearMonth.now().minusMonths(9),
                YearMonth.now().minusMonths(10),
                YearMonth.now().minusMonths(11),
                YearMonth.now().minusMonths(12)
        ));
        monthPicker.setValue(YearMonth.now());
        monthPicker.setConverter(new javafx.util.StringConverter<>() {
            private final java.time.format.DateTimeFormatter fmt =
                    java.time.format.DateTimeFormatter.ofPattern("MM/yyyy");

            @Override
            public String toString(YearMonth ym) {
                return ym == null ? "" : ym.format(fmt);
            }

            @Override
            public YearMonth fromString(String s) {
                return YearMonth.parse(s, fmt);
            }
        });

        Button btnRefresh = new Button("Atualizar");
        HBox filters = new HBox(10, new Label("Mês:"), monthPicker, btnRefresh);
        filters.setAlignment(Pos.CENTER_LEFT);
        filters.setPadding(new Insets(0, 18, 12, 18));

        VBox header = new VBox(top, kpis, filters);

        // Table
        setupTable();
        table.setItems(rows);

        // Charts
        pieByCategory.setTitle("Gastos por Categoria (Despesas)");
        pieByCategory.setLegendVisible(true);

        VBox charts = new VBox(12, pieByCategory, barType);
        charts.setPadding(new Insets(12));
        charts.setPrefWidth(420);
        charts.setStyle("""
                -fx-background-color: white;
                -fx-background-radius: 12;
                -fx-border-color: #e5e5e5;
                -fx-border-radius: 12;
                """);

        VBox tableWrap = new VBox(10, new Label("Lançamentos do mês"), table);
        tableWrap.setPadding(new Insets(12));
        tableWrap.setStyle("""
                -fx-background-color: white;
                -fx-background-radius: 12;
                -fx-border-color: #e5e5e5;
                -fx-border-radius: 12;
                """);

        HBox content = new HBox(12, tableWrap, charts);
        content.setPadding(new Insets(0, 18, 18, 18));
        HBox.setHgrow(tableWrap, Priority.ALWAYS);
        tableWrap.setPrefWidth(650);

        root.setTop(header);
        root.setCenter(content);
        root.setStyle("-fx-background-color: #f6f7fb;");

        // Actions
        btnRefresh.setOnAction(e -> refresh());
        btnAdd.setOnAction(e -> onAdd());
        btnDelete.setOnAction(e -> onDelete());
        btnLogout.setOnAction(e -> {
            authService.signOut();
            stage.setScene(new Scene(new LoginView(stage).getRoot(), 980, 640));
        });

        monthPicker.setOnAction(e -> refresh());
    }

    private Pane card(Label lbl) {
        lbl.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        VBox box = new VBox(lbl);
        box.setPadding(new Insets(14));
        box.setStyle("""
                -fx-background-color: white;
                -fx-background-radius: 12;
                -fx-border-color: #e5e5e5;
                -fx-border-radius: 12;
                """);
        box.setMinWidth(220);
        return box;
    }

    private void setupTable() {
        TableColumn<Transaction, String> colDate = new TableColumn<>("Data");
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");


        colDate.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getDate().format(fmt)));
        colDate.setPrefWidth(100);

        TableColumn<Transaction, String> colType = new TableColumn<>("Tipo");
        colType.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getType().label()));
        colType.setPrefWidth(100);

        TableColumn<Transaction, String> colCat = new TableColumn<>("Categoria");
        colCat.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getCategory().label()));
        colCat.setPrefWidth(140);

        TableColumn<Transaction, String> colAmount = new TableColumn<>("Valor");
        colAmount.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty("R$ " + c.getValue().getAmount().toPlainString()));
        colAmount.setPrefWidth(110);

        TableColumn<Transaction, String> colDesc = new TableColumn<>("Descrição");
        colDesc.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getDescription()));
        colDesc.setPrefWidth(260);

        TableColumn<Transaction, String> colDtcad = new TableColumn<>("Data de cadastro");
        java.time.format.DateTimeFormatter fmtCad =
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        colDtcad.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(
                        c.getValue().getCreated_at() != null
                                ? c.getValue().getCreated_at().format(fmtCad)
                                : ""
                )
        );
        colDtcad.setPrefWidth(180);

        table.getColumns().addAll(colDate, colType, colCat, colAmount, colDesc, colDtcad);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPrefHeight(520);
    }

    private void loadInitial() {
        refresh();
    }

    private void refresh() {
        try {
            YearMonth ym = monthPicker.getValue() != null ? monthPicker.getValue() : YearMonth.now();
            LocalDate from = ym.atDay(1);
            LocalDate to = ym.atEndOfMonth();

            List<Transaction> list = txService.listByDate(from, to);
            rows.setAll(list);

            updateKpisAndCharts(list);

        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Erro ao carregar dados: " + ex.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    private void updateKpisAndCharts(List<Transaction> list) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;

        Map<Category, BigDecimal> expenseByCat = new EnumMap<>(Category.class);
        for (Category c : Category.values()) expenseByCat.put(c, BigDecimal.ZERO);

        for (Transaction t : list) {
            if (t.getType() == TxType.INCOME) {
                income = income.add(t.getAmount());
            } else {
                expense = expense.add(t.getAmount());
                expenseByCat.put(t.getCategory(), expenseByCat.get(t.getCategory()).add(t.getAmount()));
            }
        }

        BigDecimal balance = income.subtract(expense);

        lblIncome.setText("Receitas: R$ " + income.toPlainString());
        lblExpense.setText("Despesas: R$ " + expense.toPlainString());
        lblBalance.setText("Saldo: R$ " + balance.toPlainString());

        // Pie (somente despesas por categoria)
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
        for (var entry : expenseByCat.entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                pieData.add(new PieChart.Data(entry.getKey().label(), entry.getValue().doubleValue()));
            }
        }
        pieByCategory.setData(pieData);

        // Bar (totais de receita e despesa)
        barType.getData().clear();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Total no mês");

        XYChart.Data<String, Number> receita = new XYChart.Data<>("Receita", income.doubleValue());
        XYChart.Data<String, Number> despesa = new XYChart.Data<>("Despesa", expense.doubleValue());

        series.getData().addAll(receita, despesa);
        barType.getData().add(series);

// pinta as barras depois que o gráfico renderizar
        javafx.application.Platform.runLater(() -> {
            if (receita.getNode() != null) {
                receita.getNode().setStyle("-fx-bar-fill: #2ecc71;");
            }
            if (despesa.getNode() != null) {
                despesa.getNode().setStyle("-fx-bar-fill: #e74c3c;");
            }
        });
    }

    private void onAdd() {
        TransactionFormDialog dlg = new TransactionFormDialog();
        dlg.showAndWait().ifPresent(t -> {
            try {
                txService.add(t);
                refresh();
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Erro ao salvar: " + ex.getMessage(), ButtonType.OK).showAndWait();
            }
        });
    }

    private void onDelete() {
        Transaction selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            new Alert(Alert.AlertType.INFORMATION, "Selecione um lançamento para excluir.", ButtonType.OK).showAndWait();
            return;
        }
        java.time.format.DateTimeFormatter fmt =
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Excluir lançamento de " + selected.getDate().format(fmt) + " (R$ " + selected.getAmount() + ")?",
                ButtonType.YES, ButtonType.NO);

        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try {
                    txService.deleteById(selected.getId());
                    refresh();
                } catch (Exception ex) {
                    new Alert(Alert.AlertType.ERROR, "Erro ao excluir: " + ex.getMessage(), ButtonType.OK).showAndWait();
                }
            }
        });
    }
}