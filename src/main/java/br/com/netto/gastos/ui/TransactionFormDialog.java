package br.com.netto.gastos.ui;

import br.com.netto.gastos.model.Category;
import br.com.netto.gastos.model.Transaction;
import br.com.netto.gastos.model.TxType;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public class TransactionFormDialog {

    public Optional<Transaction> showAndWait() {
        Dialog<Transaction> dialog = new Dialog<>();
        dialog.setTitle("Novo lançamento");
        dialog.setHeaderText("Adicionar receita ou despesa");

        ButtonType save = new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        ComboBox<TxType> type = new ComboBox<>();
        type.getItems().addAll(TxType.INCOME, TxType.EXPENSE);
        type.setValue(TxType.EXPENSE);

        ComboBox<Category> category = new ComboBox<>();
        category.getItems().addAll(Category.values());
        category.setValue(Category.ALIMENTACAO);

        TextField amount = new TextField();
        amount.setPromptText("Ex: 35.50");

        DatePicker date = new DatePicker(LocalDate.now());

        TextField desc = new TextField();
        desc.setPromptText("Descrição (opcional)");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(18));

        grid.add(new Label("Tipo:"), 0, 0);
        grid.add(type, 1, 0);

        grid.add(new Label("Categoria:"), 0, 1);
        grid.add(category, 1, 1);

        grid.add(new Label("Valor:"), 0, 2);
        grid.add(amount, 1, 2);

        grid.add(new Label("Data:"), 0, 3);
        grid.add(date, 1, 3);

        grid.add(new Label("Descrição:"), 0, 4);
        grid.add(desc, 1, 4);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn != save) return null;

            String raw = amount.getText().trim().replace(",", ".");
            if (raw.isBlank()) throw new IllegalArgumentException("Valor obrigatório.");

            BigDecimal v;
            try {
                v = new BigDecimal(raw);
            } catch (Exception e) {
                throw new IllegalArgumentException("Valor inválido.");
            }
            if (v.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("Valor deve ser > 0.");

            Transaction t = new Transaction();
            t.setType(type.getValue());
            t.setCategory(category.getValue());
            t.setAmount(v);
            t.setDate(date.getValue());
            t.setDescription(desc.getText().trim());
            return t;
        });

        // validação “sem estourar” exceptions feias
        final Button btnSave = (Button) dialog.getDialogPane().lookupButton(save);
        btnSave.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                dialog.getResultConverter().call(save);
            } catch (Exception ex) {
                ev.consume();
                new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK).showAndWait();
            }
        });

        return dialog.showAndWait();
    }
}