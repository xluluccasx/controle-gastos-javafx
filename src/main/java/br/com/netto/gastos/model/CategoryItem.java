package br.com.netto.gastos.model;

/**
 * Representa uma categoria carregada do Supabase e suas preferencias por usuario.
 */
public class CategoryItem {
    private String id;
    private String name;
    private TxType type;
    private boolean defaultCategory;
    private boolean hidden;
    private boolean ownedByUser;

    /** Retorna o identificador do registro. */
    public String getId() { return id; }
    /** Define o identificador do registro. */
    public void setId(String id) { this.id = id; }

    /** Retorna o nome da categoria. */
    public String getName() { return name; }
    /** Define o nome da categoria. */
    public void setName(String name) { this.name = name; }

    /** Retorna o tipo do lancamento. */
    public TxType getType() { return type; }
    /** Define o tipo do lancamento. */
    public void setType(TxType type) { this.type = type; }

    /** Informa se a categoria e padrao do sistema. */
    public boolean isDefaultCategory() { return defaultCategory; }
    /** Define se a categoria e padrao do sistema. */
    public void setDefaultCategory(boolean defaultCategory) { this.defaultCategory = defaultCategory; }

    /** Informa se a categoria esta oculta. */
    public boolean isHidden() { return hidden; }
    /** Define se a categoria esta oculta. */
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    /** Informa se a categoria foi criada pelo usuario. */
    public boolean isOwnedByUser() { return ownedByUser; }
    /** Define se a categoria pertence ao usuario. */
    public void setOwnedByUser(boolean ownedByUser) { this.ownedByUser = ownedByUser; }

    /** Retorna o texto usado para representar o objeto nos componentes visuais. */
    @Override
    public String toString() {
        return name;
    }
}
