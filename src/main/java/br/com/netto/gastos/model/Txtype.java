package br.com.netto.gastos.model;

/**
 * Define os tipos de lancamento aceitos pela aplicacao.
 */
public enum TxType {
    INCOME("Receita"),
    EXPENSE("Despesa");

    private final String label;

    TxType(String label) {
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
