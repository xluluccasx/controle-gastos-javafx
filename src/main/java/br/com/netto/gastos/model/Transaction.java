package br.com.netto.gastos.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Representa um lancamento financeiro de receita ou despesa.
 */
public class Transaction {
    private String id;
    private String userId;
    private TxType type;
    private BigDecimal amount;
    private String category;
    private String description;
    private LocalDate date;
    private LocalDateTime created_at;
    private String receiptPath;

    /** Cria um lancamento vazio para preenchimento posterior. */
    public Transaction() {}

    /** Cria um lancamento com os principais dados financeiros. */
    public Transaction(String id, String userId, TxType type, BigDecimal amount, String category, String description, LocalDate date, LocalDateTime created_at) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.category = category;
        this.description = description;
        this.date = date;
        this.created_at = created_at;
    }

    /** Retorna o identificador do registro. */
    public String getId() { return id; }
    /** Define o identificador do registro. */
    public void setId(String id) { this.id = id; }

    /** Retorna o identificador do usuario. */
    public String getUserId() { return userId; }
    /** Define o identificador do usuario. */
    public void setUserId(String userId) { this.userId = userId; }

    /** Retorna o tipo do lancamento. */
    public TxType getType() { return type; }
    /** Define o tipo do lancamento. */
    public void setType(TxType type) { this.type = type; }

    /** Retorna o valor do lancamento. */
    public BigDecimal getAmount() { return amount; }
    /** Define o valor do lancamento. */
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    /** Retorna o nome da categoria. */
    public String getCategory() { return category; }
    /** Define o nome da categoria. */
    public void setCategory(String category) { this.category = category; }

    /** Retorna a descricao do lancamento. */
    public String getDescription() { return description; }
    /** Define a descricao do lancamento. */
    public void setDescription(String description) { this.description = description; }

    /** Retorna a data do lancamento. */
    public LocalDate getDate() { return date; }
    /** Define a data do lancamento. */
    public void setDate(LocalDate date) { this.date = date; }

    /** Retorna a data e hora de criacao. */
    public LocalDateTime getCreated_at() {
        return created_at;
    }

    /** Define a data e hora de criacao. */
    public void setCreated_at(LocalDateTime created_at) {
        this.created_at = created_at;
    }

    /** Retorna o caminho do comprovante. */
    public String getReceiptPath() { return receiptPath; }
    /** Define o caminho do comprovante. */
    public void setReceiptPath(String receiptPath) { this.receiptPath = receiptPath; }
}
