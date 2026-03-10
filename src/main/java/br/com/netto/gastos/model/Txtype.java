package br.com.netto.gastos.model;

public enum TxType {
    INCOME("Receita"),
    EXPENSE("Despesa");

    private final String label;

    TxType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}