package br.com.netto.gastos.model;

public class CategoryItem {
    private String id;
    private String name;
    private TxType type;
    private boolean defaultCategory;
    private boolean hidden;
    private boolean ownedByUser;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public TxType getType() { return type; }
    public void setType(TxType type) { this.type = type; }

    public boolean isDefaultCategory() { return defaultCategory; }
    public void setDefaultCategory(boolean defaultCategory) { this.defaultCategory = defaultCategory; }

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    public boolean isOwnedByUser() { return ownedByUser; }
    public void setOwnedByUser(boolean ownedByUser) { this.ownedByUser = ownedByUser; }

    @Override
    public String toString() {
        return name;
    }
}
