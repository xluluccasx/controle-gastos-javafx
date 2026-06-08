package br.com.netto.gastos.model;

/**
 * Enumera as categorias fixas mantidas por compatibilidade com o modelo inicial.
 */
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

    /** Cria um rotulo com o estilo padrao do formulario. */
    public String label() {
        return label;
    }

    /** Retorna o texto usado para representar o objeto nos componentes visuais. */
    @Override
    public String toString() {
        return label;
    }
}
