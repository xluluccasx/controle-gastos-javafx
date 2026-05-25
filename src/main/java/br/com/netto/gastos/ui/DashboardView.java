package br.com.netto.gastos.ui;

import br.com.netto.gastos.config.Session;
import br.com.netto.gastos.model.CategoryItem;
import br.com.netto.gastos.model.Transaction;
import br.com.netto.gastos.model.TxType;
import br.com.netto.gastos.service.AuthService;
import br.com.netto.gastos.service.CategoryService;
import br.com.netto.gastos.service.TransactionService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.Node;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DashboardView {
    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Stage stage;
    private final TransactionService txService = new TransactionService();
    private final CategoryService categoryService = new CategoryService();
    private final AuthService authService = new AuthService();

    private final BorderPane root = new BorderPane();
    private final DatePicker startDate = new DatePicker();
    private final DatePicker endDate = new DatePicker();
    private final TableView<Transaction> table = new TableView<>();
    private final ObservableList<Transaction> rows = FXCollections.observableArrayList();

    private final Label incomeValue = new Label();
    private final Label expenseValue = new Label();
    private final Label balanceValue = new Label();
    private final Label incomeDelta = new Label();
    private final Label expenseDelta = new Label();
    private final Label balanceDelta = new Label();
    private final ListView<String> insights = new ListView<>();

    private final BarChart<String, Number> categoryChart;
    private final BarChart<String, Number> typeChart;
    private final LineChart<String, Number> balanceChart;
    private final Map<String, Boolean> dashboardLineVisibility = new HashMap<>();
    private final Map<String, Boolean> reportLineVisibility = new HashMap<>();

    public DashboardView(Stage stage) {
        this.stage = stage;
        categoryChart = new BarChart<>(new CategoryAxis(), new NumberAxis());
        typeChart = new BarChart<>(new CategoryAxis(), new NumberAxis());
        balanceChart = new LineChart<>(new CategoryAxis(), new NumberAxis());
        build();
        refresh();
    }

    public Parent getRoot() {
        return root;
    }

    private void build() {
        LocalDate now = LocalDate.now();
        startDate.setValue(YearMonth.from(now).atDay(1));
        endDate.setValue(now);

        Label title = new Label("Controle de Gastos");
        title.setStyle("-fx-font-size: 32px; -fx-font-weight: 800; -fx-text-fill: #172033;");
        Label subtitle = new Label("Dashboard");
        subtitle.setStyle("-fx-text-fill: #667085;");
        VBox titleBox = new VBox(4, title, subtitle);

        Label user = new Label(Session.email());
        user.setStyle("-fx-text-fill: #667085;");
        Button logout = dangerButton("Sair");
        HBox userBox = new HBox(12, user, logout);
        userBox.setAlignment(Pos.CENTER_RIGHT);

        BorderPane topbar = new BorderPane();
        topbar.setLeft(titleBox);
        topbar.setRight(userBox);
        topbar.setPadding(new Insets(24, 24, 12, 24));

        Button apply = primaryButton("Aplicar");
        Button refresh = lightButton("Atualizar");
        Button viewTransactions = lightButton("Ver lancamentos");
        Button add = secondaryButton("Novo lancamento");
        Button categories = lightButton("Gerenciar categorias");
        Button reports = lightButton("Relatorios PDF");

        HBox filters = new HBox(16,
                field("Data inicial", startDate),
                field("Data final", endDate),
                apply,
                refresh,
                reports,
                viewTransactions
        );
        filters.setAlignment(Pos.BOTTOM_LEFT);
        filters.setPadding(new Insets(16));
        filters.setStyle(cardStyle());

        GridPane kpis = new GridPane();
        kpis.setHgap(16);
        kpis.add(kpi("Receitas", incomeValue, incomeDelta), 0, 0);
        kpis.add(kpi("Despesas", expenseValue, expenseDelta), 1, 0);
        kpis.add(kpi("Saldo", balanceValue, balanceDelta), 2, 0);
        ColumnConstraints c = new ColumnConstraints();
        c.setPercentWidth(33.33);
        kpis.getColumnConstraints().addAll(c, c, c);

        VBox analysis = new VBox(10, sectionTitle("Analise do periodo"), insights);
        analysis.setPadding(new Insets(16));
        analysis.setStyle(cardStyle());
        insights.setPrefHeight(142);
        insights.setFocusTraversable(false);

        setupCharts();
        GridPane chartGrid = new GridPane();
        chartGrid.setHgap(16);
        chartGrid.setVgap(16);
        chartGrid.add(chartCard("Top categorias de despesa", categoryChart), 0, 0);
        chartGrid.add(chartCard("Receitas x despesas", typeChart), 1, 0);
        chartGrid.add(chartCard("Evolucao do saldo", balanceChart), 0, 1, 2, 1);
        ColumnConstraints chartCol = new ColumnConstraints();
        chartCol.setPercentWidth(50);
        chartGrid.getColumnConstraints().addAll(chartCol, chartCol);

        setupTable();
        Region tableHeaderSpacer = new Region();
        HBox.setHgrow(tableHeaderSpacer, Priority.ALWAYS);
        HBox tableHeader = new HBox(12, sectionTitle("Lancamentos do periodo"), tableHeaderSpacer, add, categories);
        tableHeader.setAlignment(Pos.CENTER_LEFT);
        VBox tableCard = new VBox(10, tableHeader, table);
        tableCard.setPadding(new Insets(16));
        tableCard.setStyle(cardStyle());
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox content = new VBox(16, filters, kpis, analysis, chartGrid, tableCard);
        content.setPadding(new Insets(0, 24, 24, 24));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #f4f7fb; -fx-background-color: #f4f7fb;");

        root.setTop(topbar);
        root.setCenter(scroll);
        root.setStyle("-fx-background-color: #f4f7fb;");

        apply.setOnAction(e -> refresh());
        refresh.setOnAction(e -> refresh());
        viewTransactions.setOnAction(e -> {
            scroll.setVvalue(1.0);
            table.requestFocus();
        });
        add.setOnAction(e -> onAdd());
        categories.setOnAction(e -> showCategoriesDialog());
        reports.setOnAction(e -> showReportsDialog());
        logout.setOnAction(e -> {
            authService.signOut();
            Scene scene = new Scene(new LoginView(stage).getRoot(), 980, 640);
            UiTheme.apply(scene);
            stage.setScene(scene);
        });
    }

    private void setupCharts() {
        categoryChart.setLegendVisible(false);
        categoryChart.setAnimated(false);
        categoryChart.setPrefHeight(260);
        categoryChart.setCategoryGap(14);

        typeChart.setLegendVisible(false);
        typeChart.setAnimated(false);
        typeChart.setPrefHeight(260);

        balanceChart.setLegendVisible(true);
        balanceChart.setAnimated(false);
        balanceChart.setPrefHeight(320);
        balanceChart.setCreateSymbols(false);
    }

    private void setupTable() {
        table.setItems(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPrefHeight(430);

        TableColumn<Transaction, String> colDate = column("Data", t -> t.getDate().format(DATE), 95);
        TableColumn<Transaction, String> colType = column("Tipo", t -> t.getType().label(), 95);
        TableColumn<Transaction, String> colCat = column("Categoria", Transaction::getCategory, 130);
        TableColumn<Transaction, String> colAmount = column("Valor", t -> BRL.format(t.getAmount()), 105);
        TableColumn<Transaction, String> colDesc = column("Descricao", Transaction::getDescription, 210);
        TableColumn<Transaction, String> colCreated = column("Cadastro",
                t -> t.getCreated_at() == null ? "" : t.getCreated_at().format(DATE_TIME), 150);

        TableColumn<Transaction, Void> actions = new TableColumn<>("Acao");
        actions.setPrefWidth(245);
        actions.setCellFactory(col -> new TableCell<>() {
            private final Button edit = lightButton("Editar");
            private final Button delete = dangerButton("Excluir");
            private final Button receipt = lightButton("Ver");
            private final HBox box = new HBox(8, edit, delete, receipt);
            {
                edit.setOnAction(e -> onEdit(getTableView().getItems().get(getIndex())));
                delete.setOnAction(e -> onDelete(getTableView().getItems().get(getIndex())));
                receipt.setOnAction(e -> onOpenReceipt(getTableView().getItems().get(getIndex())));
                box.setAlignment(Pos.CENTER_LEFT);
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                Transaction tx = getTableView().getItems().get(getIndex());
                receipt.setVisible(tx.getReceiptPath() != null && !tx.getReceiptPath().isBlank());
                receipt.setManaged(receipt.isVisible());
                setGraphic(box);
            }
        });

        table.getColumns().addAll(colDate, colType, colCat, colAmount, colDesc, colCreated, actions);
    }

    private TableColumn<Transaction, String> column(String title, java.util.function.Function<Transaction, String> mapper, double width) {
        TableColumn<Transaction, String> col = new TableColumn<>(title);
        col.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(Optional.ofNullable(mapper.apply(c.getValue())).orElse("")));
        col.setPrefWidth(width);
        return col;
    }

    private void refresh() {
        try {
            LocalDate start = startDate.getValue();
            LocalDate end = endDate.getValue();
            if (start == null || end == null || start.isAfter(end)) {
                alert(Alert.AlertType.ERROR, "Informe um periodo valido.");
                return;
            }
            List<Transaction> current = txService.listByDate(start, end);
            Period previousPeriod = previousPeriod(start, end);
            List<Transaction> previous = txService.listByDate(previousPeriod.start(), previousPeriod.end());
            rows.setAll(current);
            updateDashboard(current, previous, start, end, previousPeriod);
        } catch (Exception ex) {
            alert(Alert.AlertType.ERROR, "Erro ao carregar dados: " + ex.getMessage());
        }
    }

    private void updateDashboard(List<Transaction> current, List<Transaction> previous, LocalDate start, LocalDate end, Period previousPeriod) {
        Summary summary = summarize(current);
        Summary previousSummary = summarize(previous);

        incomeValue.setText(BRL.format(summary.income));
        expenseValue.setText(BRL.format(summary.expense));
        balanceValue.setText(BRL.format(summary.balance()));
        setDelta(incomeDelta, summary.income, previousSummary.income, false);
        setDelta(expenseDelta, summary.expense, previousSummary.expense, true);
        setDelta(balanceDelta, summary.balance(), previousSummary.balance(), false);

        renderInsights(summary, previousSummary, start, end, previousPeriod);
        renderCategoryChart(summary.byCategory);
        renderTypeChart(summary);
        renderBalanceChart(current, start, end);
    }

    private void setDelta(Label label, BigDecimal current, BigDecimal previous, boolean invertGood) {
        BigDecimal diff = current.subtract(previous);
        double percent = previous.compareTo(BigDecimal.ZERO) == 0
                ? (current.compareTo(BigDecimal.ZERO) == 0 ? 0 : 100)
                : diff.divide(previous.abs(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
        boolean good = invertGood ? diff.compareTo(BigDecimal.ZERO) <= 0 : diff.compareTo(BigDecimal.ZERO) >= 0;
        label.setText("Periodo anterior: " + BRL.format(previous) + " | " + BRL.format(diff) + " (" + signedPercent(percent) + ")");
        label.setStyle("-fx-font-size: 13px; -fx-text-fill: " + (diff.signum() == 0 ? "#6b7280" : good ? "#047857" : "#b91c1c") + ";");
    }

    private void renderInsights(Summary s, Summary p, LocalDate start, LocalDate end, Period previous) {
        Map.Entry<String, BigDecimal> top = s.topCategory();
        long days = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1);
        BigDecimal averageExpense = s.expense.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        BigDecimal expenseDiff = s.expense.subtract(p.expense);
        BigDecimal balanceDiff = s.balance().subtract(p.balance());

        List<String> lines = new ArrayList<>();
        lines.add("Periodo analisado: " + start.format(DATE) + " a " + end.format(DATE)
                + ". Comparacao automatica com " + previous.start().format(DATE) + " a " + previous.end().format(DATE) + ".");
        lines.add(top == null
                ? "Nao ha despesas registradas por categoria neste periodo."
                : "Maior categoria de despesa: " + top.getKey() + ", com " + BRL.format(top.getValue()) + " (" + percentOf(top.getValue(), s.expense) + " das despesas).");
        lines.add(expenseDiff.signum() > 0
                ? "As despesas aumentaram " + BRL.format(expenseDiff) + " em relacao ao periodo anterior."
                : expenseDiff.signum() < 0
                ? "As despesas reduziram " + BRL.format(expenseDiff.abs()) + " em relacao ao periodo anterior."
                : "As despesas ficaram iguais ao periodo anterior.");
        lines.add(balanceDiff.signum() >= 0
                ? "O saldo melhorou " + BRL.format(balanceDiff.abs()) + " frente ao periodo anterior."
                : "O saldo piorou " + BRL.format(balanceDiff.abs()) + " frente ao periodo anterior.");
        lines.add("Media diaria de despesas no periodo: " + BRL.format(averageExpense) + ".");
        insights.setItems(FXCollections.observableArrayList(lines));
    }

    private void renderCategoryChart(Map<String, BigDecimal> byCategory) {
        categoryChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        byCategory.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(8)
                .forEach(e -> series.getData().add(new XYChart.Data<>(e.getKey(), e.getValue())));
        categoryChart.getData().add(series);
        Platform.runLater(() -> series.getData().forEach(d -> d.getNode().setStyle("-fx-bar-fill: #2f6fed;")));
    }

    private void renderCategoryChart(BarChart<String, Number> chart, Map<String, BigDecimal> byCategory) {
        chart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        byCategory.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(8)
                .forEach(e -> series.getData().add(new XYChart.Data<>(e.getKey(), e.getValue())));
        chart.getData().add(series);
        Platform.runLater(() -> series.getData().forEach(d -> d.getNode().setStyle("-fx-bar-fill: #2f6fed;")));
    }

    private void renderTypeChart(Summary s) {
        typeChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        XYChart.Data<String, Number> income = new XYChart.Data<>("Receitas", s.income);
        XYChart.Data<String, Number> expense = new XYChart.Data<>("Despesas", s.expense);
        series.getData().addAll(income, expense);
        typeChart.getData().add(series);
        Platform.runLater(() -> {
            if (income.getNode() != null) income.getNode().setStyle("-fx-bar-fill: #0f9f8f;");
            if (expense.getNode() != null) expense.getNode().setStyle("-fx-bar-fill: #dc2626;");
        });
    }

    private void renderTypeChart(BarChart<String, Number> chart, Summary s) {
        chart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        XYChart.Data<String, Number> income = new XYChart.Data<>("Receitas", s.income);
        XYChart.Data<String, Number> expense = new XYChart.Data<>("Despesas", s.expense);
        series.getData().addAll(income, expense);
        chart.getData().add(series);
        Platform.runLater(() -> {
            if (income.getNode() != null) income.getNode().setStyle("-fx-bar-fill: #0f9f8f;");
            if (expense.getNode() != null) expense.getNode().setStyle("-fx-bar-fill: #dc2626;");
        });
    }

    private void renderBalanceChart(List<Transaction> list, LocalDate start, LocalDate end) {
        balanceChart.getData().clear();
        renderBalanceChart(balanceChart, list, start, end, dashboardLineVisibility);
    }

    private void renderBalanceChart(LineChart<String, Number> chart, List<Transaction> list, LocalDate start, LocalDate end, Map<String, Boolean> visibility) {
        chart.getData().clear();
        Map<LocalDate, Summary> byDate = new HashMap<>();
        for (Transaction tx : list) {
            Summary daily = byDate.computeIfAbsent(tx.getDate(), k -> new Summary());
            daily.add(tx);
        }
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        XYChart.Series<String, Number> incomeSeries = new XYChart.Series<>();
        incomeSeries.setName("Receitas acumuladas");
        XYChart.Series<String, Number> expenseSeries = new XYChart.Series<>();
        expenseSeries.setName("Despesas acumuladas");
        XYChart.Series<String, Number> balanceSeries = new XYChart.Series<>();
        balanceSeries.setName("Saldo acumulado");
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            Summary daily = byDate.get(d);
            if (daily != null) {
                income = income.add(daily.income);
                expense = expense.add(daily.expense);
            }
            String label = d.format(DateTimeFormatter.ofPattern("dd/MM"));
            incomeSeries.getData().add(new XYChart.Data<>(label, income));
            expenseSeries.getData().add(new XYChart.Data<>(label, expense));
            balanceSeries.getData().add(new XYChart.Data<>(label, income.subtract(expense)));
        }
        chart.getData().addAll(incomeSeries, expenseSeries, balanceSeries);
        Platform.runLater(() -> {
            styleLine(incomeSeries, "#0f9f8f");
            styleLine(expenseSeries, "#dc2626");
            styleLine(balanceSeries, "#2f6fed");
            installLegendToggles(chart, visibility);
        });
    }

    private void styleLine(XYChart.Series<String, Number> series, String color) {
        if (series.getNode() != null) {
            series.getNode().setStyle("-fx-stroke: " + color + "; -fx-stroke-width: 2.5px;");
        }
        for (XYChart.Data<String, Number> data : series.getData()) {
            if (data.getNode() != null) {
                data.getNode().setStyle("-fx-background-color: " + color + ", white;");
            }
        }
    }

    private void installLegendToggles(LineChart<String, Number> chart, Map<String, Boolean> visibility) {
        for (XYChart.Series<String, Number> series : chart.getData()) {
            visibility.putIfAbsent(series.getName(), true);
            applySeriesVisibility(series, visibility.get(series.getName()));
        }

        for (Node legendItem : chart.lookupAll(".chart-legend-item")) {
            Label label = findLegendLabel(legendItem);
            if (label == null) {
                continue;
            }

            String seriesName = label.getText();
            Node symbol = legendItem.lookup(".chart-legend-item-symbol");
            if (symbol != null) {
                symbol.setStyle("-fx-background-color: " + colorForSeries(seriesName) + "; -fx-background-radius: 3;");
            }
            legendItem.setStyle("-fx-cursor: hand;");
            legendItem.setOnMouseClicked(e -> {
                boolean visible = !visibility.getOrDefault(seriesName, true);
                visibility.put(seriesName, visible);

                chart.getData().stream()
                        .filter(series -> seriesName.equals(series.getName()))
                        .findFirst()
                        .ifPresent(series -> applySeriesVisibility(series, visible));

                legendItem.setOpacity(visible ? 1.0 : 0.35);
            });
            legendItem.setOpacity(visibility.getOrDefault(seriesName, true) ? 1.0 : 0.35);
        }
    }

    private String colorForSeries(String seriesName) {
        return switch (seriesName) {
            case "Receitas acumuladas" -> "#0f9f8f";
            case "Despesas acumuladas" -> "#dc2626";
            case "Saldo acumulado" -> "#2f6fed";
            default -> "#2f6fed";
        };
    }

    private Label findLegendLabel(Node node) {
        if (node instanceof Label label) {
            return label;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Label label = findLegendLabel(child);
                if (label != null) {
                    return label;
                }
            }
        }
        return null;
    }

    private void applySeriesVisibility(XYChart.Series<String, Number> series, boolean visible) {
        if (series.getNode() != null) {
            series.getNode().setVisible(visible);
            series.getNode().setManaged(visible);
        }
        for (XYChart.Data<String, Number> data : series.getData()) {
            if (data.getNode() != null) {
                data.getNode().setVisible(visible);
                data.getNode().setManaged(visible);
            }
        }
    }

    private void onAdd() {
        boolean keepAdding;
        do {
            keepAdding = new TransactionFormDialog().showAndWait(stage, null).map(result -> {
                try {
                    Transaction saved = txService.add(result.transaction());
                    if (result.receiptFile() != null) {
                        saved.setReceiptPath(txService.uploadReceipt(saved.getId(), result.receiptFile()));
                        txService.update(saved);
                    }
                    refresh();
                    return result.continueAdding();
                } catch (Exception ex) {
                    alert(Alert.AlertType.ERROR, "Erro ao salvar: " + ex.getMessage());
                    return false;
                }
            }).orElse(false);
        } while (keepAdding);
    }

    private void onEdit(Transaction selected) {
        new TransactionFormDialog().showAndWait(stage, selected).ifPresent(result -> {
            try {
                Transaction tx = result.transaction();
                if (result.receiptFile() != null) {
                    tx.setReceiptPath(txService.uploadReceipt(tx.getId(), result.receiptFile()));
                }
                txService.update(tx);
                refresh();
            } catch (Exception ex) {
                alert(Alert.AlertType.ERROR, "Erro ao alterar lancamento: " + ex.getMessage());
            }
        });
    }

    private void onDelete(Transaction selected) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Excluir lancamento de " + selected.getDate().format(DATE) + " (" + BRL.format(selected.getAmount()) + ")?",
                ButtonType.YES, ButtonType.NO);
        confirm.initOwner(stage);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                try {
                    txService.deleteById(selected.getId());
                    refresh();
                } catch (Exception ex) {
                    alert(Alert.AlertType.ERROR, "Erro ao excluir: " + ex.getMessage());
                }
            }
        });
    }

    private void onOpenReceipt(Transaction tx) {
        try {
            String url = txService.createReceiptUrl(tx.getReceiptPath());
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ex) {
            alert(Alert.AlertType.ERROR, "Erro ao abrir comprovante: " + ex.getMessage());
        }
    }

    private void showCategoriesDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Gerenciar categorias");
        dialog.initOwner(stage);
        UiTheme.apply(dialog.getDialogPane());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        TextField name = new TextField();
        name.setPromptText("Nova categoria");
        ComboBox<TxType> type = new ComboBox<>(FXCollections.observableArrayList(TxType.values()));
        type.setValue(TxType.EXPENSE);
        Button add = primaryButton("Adicionar");
        ListView<CategoryItem> list = new ListView<>();
        list.setPrefHeight(390);

        Runnable load = () -> {
            try {
                list.setItems(FXCollections.observableArrayList(categoryService.listForCurrentUser()));
            } catch (Exception ex) {
                alert(Alert.AlertType.ERROR, "Erro ao carregar categorias: " + ex.getMessage());
            }
        };

        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(CategoryItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Label label = new Label(item.getName() + " (" + item.getType().label() + ")" + (item.isDefaultCategory() ? " *" : ""));
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                HBox actions = new HBox(8);
                if (item.isDefaultCategory()) {
                    CheckBox hidden = new CheckBox("Ocultar");
                    hidden.setSelected(item.isHidden());
                    hidden.setOnAction(e -> {
                        try {
                            categoryService.setHidden(item.getId(), hidden.isSelected());
                            load.run();
                        } catch (Exception ex) {
                            alert(Alert.AlertType.ERROR, "Erro ao salvar categoria: " + ex.getMessage());
                        }
                    });
                    actions.getChildren().add(hidden);
                } else {
                    Button edit = lightButton("Editar");
                    Button del = dangerButton("Excluir");
                    edit.setOnAction(e -> {
                        TextInputDialog input = new TextInputDialog(item.getName());
                        input.setTitle("Editar categoria");
                        input.setHeaderText("Novo nome");
                        input.initOwner(stage);
                        input.showAndWait().ifPresent(newName -> {
                            try {
                                categoryService.rename(item.getId(), newName.trim());
                                load.run();
                            } catch (Exception ex) {
                                alert(Alert.AlertType.ERROR, "Erro ao editar categoria: " + ex.getMessage());
                            }
                        });
                    });
                    del.setOnAction(e -> {
                        try {
                            categoryService.deleteCustom(item.getId());
                            load.run();
                        } catch (Exception ex) {
                            alert(Alert.AlertType.ERROR, "Erro ao excluir categoria: " + ex.getMessage());
                        }
                    });
                    actions.getChildren().addAll(edit, del);
                }
                HBox row = new HBox(12, label, spacer, actions);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(8, 0, 8, 0));
                setGraphic(row);
            }
        });

        add.setOnAction(e -> {
            try {
                if (name.getText().trim().isBlank()) {
                    alert(Alert.AlertType.ERROR, "Informe o nome da categoria.");
                    return;
                }
                categoryService.add(name.getText().trim(), type.getValue());
                name.clear();
                load.run();
            } catch (Exception ex) {
                alert(Alert.AlertType.ERROR, "Erro ao salvar categoria: " + ex.getMessage());
            }
        });

        HBox form = new HBox(12, field("Nova categoria", name), field("Tipo", type), add);
        form.setAlignment(Pos.BOTTOM_LEFT);
        VBox content = new VBox(16, sectionTitle("Gerenciar Categorias"), form, list);
        content.setPadding(new Insets(18));
        content.setPrefWidth(760);
        dialog.getDialogPane().setContent(content);
        load.run();
        dialog.showAndWait();
    }

    private void showReportsDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Relatorios PDF");
        dialog.initOwner(stage);
        UiTheme.apply(dialog.getDialogPane());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        ComboBox<String> reportType = new ComboBox<>(FXCollections.observableArrayList(
                "Resumo financeiro do periodo",
                "Comparacao entre periodos",
                "Gastos por categoria"
        ));
        reportType.setValue("Resumo financeiro do periodo");
        DatePicker reportStart = new DatePicker(startDate.getValue());
        DatePicker reportEnd = new DatePicker(endDate.getValue());
        DatePicker compareStart = new DatePicker(YearMonth.now().minusMonths(1).atDay(1));
        DatePicker compareEnd = new DatePicker(YearMonth.now().minusMonths(1).atEndOfMonth());
        TextArea preview = new TextArea();
        preview.setEditable(false);
        preview.setPrefHeight(240);

        BarChart<String, Number> reportTypeChart = new BarChart<>(new CategoryAxis(), new NumberAxis());
        BarChart<String, Number> reportCategoryChart = new BarChart<>(new CategoryAxis(), new NumberAxis());
        LineChart<String, Number> reportBalanceChart = new LineChart<>(new CategoryAxis(), new NumberAxis());
        reportTypeChart.setLegendVisible(false);
        reportCategoryChart.setLegendVisible(false);
        reportBalanceChart.setLegendVisible(true);
        reportTypeChart.setAnimated(false);
        reportCategoryChart.setAnimated(false);
        reportBalanceChart.setAnimated(false);
        reportBalanceChart.setCreateSymbols(false);
        reportTypeChart.setPrefHeight(220);
        reportCategoryChart.setPrefHeight(220);
        reportBalanceChart.setPrefHeight(260);

        Button previewButton = primaryButton("Visualizar relatorio");
        Button pdfButton = secondaryButton("Baixar PDF");
        pdfButton.setDisable(true);

        final ReportData[] report = new ReportData[1];
        previewButton.setOnAction(e -> {
            try {
                report[0] = buildReport(reportType.getSelectionModel().getSelectedIndex(), reportStart.getValue(), reportEnd.getValue(), compareStart.getValue(), compareEnd.getValue());
                preview.setText(report[0].text());
                renderTypeChart(reportTypeChart, report[0].summary());
                renderCategoryChart(reportCategoryChart, report[0].summary().byCategory);
                renderBalanceChart(reportBalanceChart, report[0].transactions(), reportStart.getValue(), reportEnd.getValue(), reportLineVisibility);
                pdfButton.setDisable(false);
            } catch (Exception ex) {
                alert(Alert.AlertType.ERROR, "Erro ao gerar relatorio: " + ex.getMessage());
            }
        });
        pdfButton.setOnAction(e -> {
            try {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Salvar relatorio PDF");
                chooser.setInitialFileName("relatorio-" + LocalDate.now() + ".pdf");
                chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
                var file = chooser.showSaveDialog(stage);
                if (file != null) {
                    writeSimplePdf(file.toPath(), report[0].text());
                }
            } catch (Exception ex) {
                alert(Alert.AlertType.ERROR, "Erro ao salvar PDF: " + ex.getMessage());
            }
        });

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);
        form.add(field("Tipo de relatorio", reportType), 0, 0, 2, 1);
        form.add(field("Data inicial", reportStart), 0, 1);
        form.add(field("Data final", reportEnd), 1, 1);
        HBox comparisonFields = new HBox(12, field("Comparar inicial", compareStart), field("Comparar final", compareEnd));
        form.add(comparisonFields, 0, 2, 2, 1);
        form.add(new HBox(10, previewButton, pdfButton), 0, 3, 2, 1);

        Runnable updateComparisonVisibility = () -> {
            boolean comparison = reportType.getSelectionModel().getSelectedIndex() == 1;
            comparisonFields.setVisible(comparison);
            comparisonFields.setManaged(comparison);
        };
        reportType.setOnAction(e -> {
            updateComparisonVisibility.run();
            pdfButton.setDisable(true);
            preview.clear();
        });
        updateComparisonVisibility.run();

        GridPane reportCharts = new GridPane();
        reportCharts.setHgap(16);
        reportCharts.setVgap(16);
        reportCharts.add(chartCard("Receitas x despesas", reportTypeChart), 0, 0);
        reportCharts.add(chartCard("Top categorias de despesa", reportCategoryChart), 1, 0);
        reportCharts.add(chartCard("Evolucao do saldo", reportBalanceChart), 0, 1, 2, 1);
        ColumnConstraints reportChartCol = new ColumnConstraints();
        reportChartCol.setPercentWidth(50);
        reportCharts.getColumnConstraints().addAll(reportChartCol, reportChartCol);

        VBox content = new VBox(16, sectionTitle("Exportar relatorio"), form, sectionTitle("Pre-visualizacao"), preview, reportCharts);
        content.setPadding(new Insets(18));
        content.setPrefWidth(820);

        double maxWidth = Math.min(900, Screen.getPrimary().getVisualBounds().getWidth() - 80);
        double maxHeight = Math.min(760, Screen.getPrimary().getVisualBounds().getHeight() - 80);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(maxWidth);
        scroll.setPrefViewportHeight(maxHeight);
        scroll.setMaxWidth(maxWidth);
        scroll.setMaxHeight(maxHeight);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().setMaxWidth(maxWidth + 40);
        dialog.getDialogPane().setMaxHeight(maxHeight + 80);
        dialog.showAndWait();
    }

    private ReportData buildReport(int type, LocalDate start, LocalDate end, LocalDate compareStart, LocalDate compareEnd) throws Exception {
        if (start == null || end == null || start.isAfter(end)) {
            throw new IllegalArgumentException("Informe o periodo principal corretamente.");
        }
        List<Transaction> primary = txService.listByDate(start, end);
        Summary summary = summarize(primary);
        StringBuilder text = new StringBuilder();
        text.append(type == 1 ? "Comparacao entre periodos" : type == 2 ? "Gastos por categoria" : "Resumo financeiro do periodo").append("\n");
        text.append("Periodo principal: ").append(start.format(DATE)).append(" a ").append(end.format(DATE)).append("\n\n");
        appendSummary(text, summary);

        if (type == 1) {
            if (compareStart == null || compareEnd == null || compareStart.isAfter(compareEnd)) {
                throw new IllegalArgumentException("Informe o periodo de comparacao corretamente.");
            }
            Summary comparison = summarize(txService.listByDate(compareStart, compareEnd));
            text.append("\nPeriodo comparado: ").append(compareStart.format(DATE)).append(" a ").append(compareEnd.format(DATE)).append("\n");
            appendSummary(text, comparison);
            text.append("\nAnalise do periodo\n");
            text.append(buildComparisonInsight(summary, comparison)).append("\n");
        } else if (type == 2) {
            text.append("\nCategorias\n");
            summary.byCategory.entrySet().stream()
                    .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                    .forEach(e -> text.append(e.getKey()).append(": ").append(BRL.format(e.getValue())).append("\n"));
        } else {
            text.append("\nLancamentos\n");
            for (Transaction tx : primary) {
                text.append(tx.getDate().format(DATE)).append(" | ")
                        .append(tx.getType().label()).append(" | ")
                        .append(tx.getCategory()).append(" | ")
                        .append(BRL.format(tx.getAmount())).append(" | ")
                        .append(Optional.ofNullable(tx.getDescription()).orElse("")).append("\n");
            }
        }
        return new ReportData(text.toString(), primary, summary);
    }

    private void appendSummary(StringBuilder text, Summary summary) {
        text.append("Receitas: ").append(BRL.format(summary.income)).append("\n");
        text.append("Despesas: ").append(BRL.format(summary.expense)).append("\n");
        text.append("Saldo: ").append(BRL.format(summary.balance())).append("\n");
        text.append("Lancamentos: ").append(summary.count).append("\n");
    }

    private String buildComparisonInsight(Summary primary, Summary comparison) {
        BigDecimal balanceDiff = primary.balance().subtract(comparison.balance());
        BigDecimal expenseDiff = primary.expense.subtract(comparison.expense);
        return (balanceDiff.signum() >= 0
                ? "O periodo principal teve saldo " + BRL.format(balanceDiff.abs()) + " melhor que o comparado. "
                : "O periodo principal teve saldo " + BRL.format(balanceDiff.abs()) + " pior que o comparado. ")
                + (expenseDiff.signum() <= 0
                ? "Despesas reduziram " + BRL.format(expenseDiff.abs()) + "."
                : "Despesas aumentaram " + BRL.format(expenseDiff) + ".");
    }

    private Summary summarize(List<Transaction> list) {
        Summary s = new Summary();
        list.forEach(s::add);
        return s;
    }

    private Period previousPeriod(LocalDate start, LocalDate end) {
        long days = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1);
        LocalDate previousEnd = start.minusDays(1);
        return new Period(previousEnd.minusDays(days - 1), previousEnd);
    }

    private VBox kpi(String title, Label value, Label delta) {
        Label label = new Label(title);
        label.setStyle("-fx-text-fill: #667085; -fx-font-weight: 700;");
        value.setStyle("-fx-font-size: 28px; -fx-font-weight: 800; -fx-text-fill: #172033;");
        VBox box = new VBox(8, label, value, delta);
        box.setPadding(new Insets(16));
        box.setStyle(cardStyle());
        box.setMinWidth(220);
        return box;
    }

    private VBox chartCard(String title, Chart chart) {
        VBox box = new VBox(10, sectionTitle(title), chart);
        box.setPadding(new Insets(16));
        box.setStyle(cardStyle());
        return box;
    }

    private VBox field(String label, Control control) {
        Label l = new Label(label);
        l.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #667085;");
        control.setPrefWidth(170);
        return new VBox(4, l, control);
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #172033;");
        return label;
    }

    private Button primaryButton(String text) {
        return button(text, "#2f6fed", "#ffffff");
    }

    private Button secondaryButton(String text) {
        return button(text, "#0f9f8f", "#ffffff");
    }

    private Button dangerButton(String text) {
        return button(text, "#dc2626", "#ffffff");
    }

    private Button lightButton(String text) {
        return button(text, "#e9eef5", "#172033");
    }

    private Button button(String text, String bg, String fg) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; -fx-font-weight: 700; -fx-background-radius: 8; -fx-padding: 10 15; -fx-cursor: hand;");
        return b;
    }

    private String cardStyle() {
        return "-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #dbe3ee; -fx-border-radius: 8; -fx-effect: dropshadow(three-pass-box, rgba(15,23,42,0.08), 22, 0, 0, 8);";
    }

    private void alert(Alert.AlertType type, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.initOwner(stage);
        alert.showAndWait();
    }

    private String percentOf(BigDecimal value, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return "0,0%";
        }
        double percent = value.divide(total, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
        return String.format(Locale.forLanguageTag("pt-BR"), "%.1f%%", percent);
    }

    private String signedPercent(double value) {
        return (value >= 0 ? "+" : "") + String.format(Locale.forLanguageTag("pt-BR"), "%.1f%%", value);
    }

    private void writeSimplePdf(Path path, String text) throws Exception {
        List<String> lines = Arrays.stream(text.split("\\R"))
                .flatMap(line -> wrap(line, 94).stream())
                .toList();
        StringBuilder content = new StringBuilder("BT\n/F1 11 Tf\n50 790 Td\n14 TL\n");
        int lineCount = 0;
        for (String line : lines) {
            if (lineCount > 0 && lineCount % 52 == 0) {
                content.append("ET\nBT\n/F1 11 Tf\n50 790 Td\n14 TL\n");
            }
            content.append("(").append(pdfEscape(line)).append(") Tj\nT*\n");
            lineCount++;
        }
        content.append("ET\n");
        byte[] stream = content.toString().getBytes(StandardCharsets.ISO_8859_1);

        String header = "%PDF-1.4\n";
        List<String> objects = List.of(
                "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n",
                "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n",
                "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >> endobj\n",
                "4 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n",
                "5 0 obj << /Length " + stream.length + " >> stream\n" + content + "endstream endobj\n"
        );
        StringBuilder pdf = new StringBuilder(header);
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        for (String obj : objects) {
            offsets.add(pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length);
            pdf.append(obj);
        }
        int xref = pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length;
        pdf.append("xref\n0 6\n0000000000 65535 f \n");
        for (int i = 1; i < offsets.size(); i++) {
            pdf.append(String.format("%010d 00000 n \n", offsets.get(i)));
        }
        pdf.append("trailer << /Size 6 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF");
        Files.writeString(path, pdf.toString(), StandardCharsets.ISO_8859_1);
    }

    private List<String> wrap(String line, int size) {
        if (line.length() <= size) return List.of(line);
        List<String> out = new ArrayList<>();
        String current = line;
        while (current.length() > size) {
            int idx = current.lastIndexOf(' ', size);
            if (idx <= 0) idx = size;
            out.add(current.substring(0, idx));
            current = current.substring(idx).trim();
        }
        out.add(current);
        return out;
    }

    private String pdfEscape(String line) {
        return line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private record Period(LocalDate start, LocalDate end) {}
    private record ReportData(String text, List<Transaction> transactions, Summary summary) {}

    private static class Summary {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        int count;
        Map<String, BigDecimal> byCategory = new HashMap<>();

        void add(Transaction tx) {
            count++;
            if (tx.getType() == TxType.INCOME) {
                income = income.add(tx.getAmount());
            } else {
                expense = expense.add(tx.getAmount());
                String category = tx.getCategory() == null || tx.getCategory().isBlank() ? "Sem categoria" : tx.getCategory();
                byCategory.put(category, byCategory.getOrDefault(category, BigDecimal.ZERO).add(tx.getAmount()));
            }
        }

        BigDecimal balance() {
            return income.subtract(expense);
        }

        Map.Entry<String, BigDecimal> topCategory() {
            return byCategory.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);
        }
    }
}
