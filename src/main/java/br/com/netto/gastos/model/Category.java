package br.com.netto.gastos.model;

public enum Category {
    ALIMENTACAO("Alimentação"),
    TRANSPORTE("Transporte"),
    MORADIA("Moradia"),
    SAUDE("Saúde"),
    EDUCACAO("Educação"),
    LAZER("Lazer"),
    CONTAS("Contas"),
    OUTROS("Outros");

    private final String label;

    Category(String label) {
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