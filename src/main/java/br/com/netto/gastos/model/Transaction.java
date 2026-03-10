package br.com.netto.gastos.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Transaction {
    private String id;
    private String userId;
    private TxType type;
    private BigDecimal amount;
    private Category category;
    private String description;
    private LocalDate date;

    public Transaction() {}

    public Transaction(String id, String userId, TxType type, BigDecimal amount, Category category, String description, LocalDate date) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.category = category;
        this.description = description;
        this.date = date;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public TxType getType() { return type; }
    public void setType(TxType type) { this.type = type; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
}