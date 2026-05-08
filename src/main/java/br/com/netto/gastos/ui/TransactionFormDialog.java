package br.com.netto.gastos.ui;

import br.com.netto.gastos.model.CategoryItem;
import br.com.netto.gastos.model.Transaction;
import br.com.netto.gastos.model.TxType;
import br.com.netto.gastos.service.CategoryService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class TransactionFormDialog {
    private final CategoryService categoryService = new CategoryService();
    private Path selectedReceipt;

    public Optional<Result> showAndWait(Window owner, Transaction existing) {
        Dialog<Result> dialog = new Dialog<>();
        boolean editing = existing != null;
        dialog.setTitle(editing ? "Editar lancamento" : "Novo lancamento");
        dialog.setHeaderText(editing ? "Salvar alteracoes do lancamento" : "Adicionar receita ou despesa");
        dialog.initOwner(owner);

        ButtonType save = new ButtonType(editing ? "Salvar alteracoes" : "Salvar lancamento", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        dialog.getDialogPane().setStyle("-fx-background-color: #ffffff;");

        ComboBox<TxType> type = new ComboBox<>();
        type.getItems().addAll(TxType.INCOME, TxType.EXPENSE);
        type.setValue(editing ? existing.getType() : TxType.EXPENSE);

        ComboBox<String> category = new ComboBox<>();
        category.setPrefWidth(260);

        TextField amount = new TextField(editing ? existing.getAmount().toPlainString() : "");
        amount.setPromptText("Ex: 35.50");

        DatePicker date = new DatePicker(editing ? existing.getDate() : LocalDate.now());

        TextField desc = new TextField(editing ? existing.getDescription() : "");
        desc.setPromptText("Descricao do lancamento");

        Button receiptButton = new Button(editing ? "Alterar comprovante" : "Selecionar comprovante");
        Label receiptLabel = new Label(editing && existing.getReceiptPath() != null ? "Comprovante atual cadastrado" : "Nenhum arquivo selecionado");
        receiptLabel.setStyle("-fx-text-fill: #6b7280;");

        receiptButton.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Selecionar comprovante");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Imagens e PDFs", "*.png", "*.jpg", "*.jpeg", "*.webp", "*.pdf"),
                    new FileChooser.ExtensionFilter("Todos os arquivos", "*.*")
            );
            var file = chooser.showOpenDialog(owner);
            if (file != null) {
                selectedReceipt = file.toPath();
                receiptLabel.setText(file.getName());
            }
        });

        Runnable loadCategories = () -> {
            try {
                List<CategoryItem> items = categoryService.listVisibleByType(type.getValue());
                category.setItems(FXCollections.observableArrayList(items.stream().map(CategoryItem::getName).toList()));
                if (editing && existing.getCategory() != null && existing.getType() == type.getValue()) {
                    if (!category.getItems().contains(existing.getCategory())) {
                        category.getItems().add(existing.getCategory());
                    }
                    category.setValue(existing.getCategory());
                } else if (!category.getItems().isEmpty()) {
                    category.setValue(category.getItems().get(0));
                }
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Erro ao carregar categorias: " + ex.getMessage(), ButtonType.OK).showAndWait();
            }
        };
        type.setOnAction(e -> loadCategories.run());
        loadCategories.run();

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(12);
        grid.setPadding(new Insets(18));
        grid.setStyle("-fx-background-color: #ffffff;");

        grid.add(label("Tipo"), 0, 0);
        grid.add(type, 1, 0);
        grid.add(label("Categoria"), 0, 1);
        grid.add(category, 1, 1);
        grid.add(label("Valor"), 0, 2);
        grid.add(amount, 1, 2);
        grid.add(label("Data"), 0, 3);
        grid.add(date, 1, 3);
        grid.add(label("Descricao"), 0, 4);
        grid.add(desc, 1, 4);
        grid.add(label("Comprovante"), 0, 5);
        grid.add(new javafx.scene.layout.VBox(8, receiptButton, receiptLabel), 1, 5);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn != save) return null;
            Transaction t = editing ? existing : new Transaction();
            t.setType(type.getValue());
            t.setCategory(category.getValue());
            t.setAmount(parseAmount(amount.getText()));
            t.setDate(date.getValue());
            t.setDescription(desc.getText().trim());
            return new Result(t, selectedReceipt);
        });

        final Button btnSave = (Button) dialog.getDialogPane().lookupButton(save);
        btnSave.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                if (category.getValue() == null || category.getValue().isBlank()) {
                    throw new IllegalArgumentException("Selecione uma categoria.");
                }
                if (date.getValue() == null) {
                    throw new IllegalArgumentException("Informe a data.");
                }
                parseAmount(amount.getText());
            } catch (Exception ex) {
                ev.consume();
                new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK).showAndWait();
            }
        });

        return dialog.showAndWait();
    }

    private static Label label(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: 600;");
        return label;
    }

    private static BigDecimal parseAmount(String rawValue) {
        String raw = rawValue == null ? "" : rawValue.trim().replace(",", ".");
        if (raw.isBlank()) throw new IllegalArgumentException("Valor obrigatorio.");
        BigDecimal value;
        try {
            value = new BigDecimal(raw);
        } catch (Exception e) {
            throw new IllegalArgumentException("Valor invalido.");
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("Valor deve ser maior que zero.");
        return value;
    }

    public record Result(Transaction transaction, Path receiptFile) {}
}
