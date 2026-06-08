package br.com.netto.gastos.service;

import br.com.netto.gastos.config.AppConfig;
import br.com.netto.gastos.config.Session;
import br.com.netto.gastos.model.Transaction;
import br.com.netto.gastos.model.TxType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Gerencia lancamentos e comprovantes financeiros no Supabase.
 */
public class TransactionService {

    private final SupabaseClient client = new SupabaseClient();
    private final ObjectMapper mapper = new ObjectMapper();

    /** Monta o endereco REST da tabela de lancamentos. */
    private String restBase() {
        return AppConfig.SUPABASE_URL + "/rest/v1/" + AppConfig.TABLE_TRANSACTIONS;
    }

    /** Cria uma categoria personalizada e a vincula ao usuario. */
    public Transaction add(Transaction t) throws Exception {
        // Importante: user_id vem da Session (não confie em input)
        t.setUserId(Session.userId());

        String body = """
                {
                  "user_id": "%s",
                  "type": "%s",
                  "amount": %s,
                  "category": "%s",
                  "description": %s,
                  "date": "%s"
                }
                """.formatted(
                t.getUserId(),
                t.getType().name(),
                t.getAmount().toPlainString(),
                escapeJson(t.getCategory()),
                (t.getDescription() == null || t.getDescription().isBlank())
                        ? "null"
                        : "\"" + escapeJson(t.getDescription()) + "\"",
                t.getDate().toString()
        );

        // return=representation faz o Supabase devolver a linha criada
        String json = client.postJson(restBase(), body, "return=representation");
        JsonNode arr = mapper.readTree(json);
        if (!arr.isArray() || arr.size() == 0) return t;

        return parseTransaction(arr.get(0));
    }

    /** Atualiza um lancamento e, quando informado, seu comprovante. */
    public void update(Transaction t) throws Exception {
        String body = """
                {
                  "type": "%s",
                  "amount": %s,
                  "category": "%s",
                  "description": %s,
                  "date": "%s",
                  "receipt_path": %s
                }
                """.formatted(
                t.getType().name(),
                t.getAmount().toPlainString(),
                escapeJson(t.getCategory()),
                (t.getDescription() == null || t.getDescription().isBlank())
                        ? "null"
                        : "\"" + escapeJson(t.getDescription()) + "\"",
                t.getDate().toString(),
                t.getReceiptPath() == null || t.getReceiptPath().isBlank()
                        ? "null"
                        : "\"" + escapeJson(t.getReceiptPath()) + "\""
        );

        String url = restBase()
                + "?id=eq." + encode(t.getId())
                + "&user_id=eq." + encode(Session.userId());
        client.patchJson(url, body, null);
    }

    /** Busca um lancamento pelo identificador. */
    public Transaction findById(String id) throws Exception {
        String url = restBase()
                + "?id=eq." + encode(id)
                + "&user_id=eq." + encode(Session.userId())
                + "&limit=1";
        String json = client.get(url);
        JsonNode arr = mapper.readTree(json);
        if (!arr.isArray() || arr.isEmpty()) {
            return null;
        }
        return parseTransaction(arr.get(0));
    }

    /** Lista os lancamentos do usuario dentro do periodo informado. */
    public List<Transaction> listByDate(LocalDate from, LocalDate to) throws Exception {
        String url = restBase()
                + "?user_id=eq." + Session.userId()
                + "&date=gte." + from
                + "&date=lte." + to
                + "&order=date.desc";

        String json = client.get(url);
        JsonNode arr = mapper.readTree(json);

        List<Transaction> out = new ArrayList<>();
        if (arr.isArray()) {
            for (JsonNode n : arr) out.add(parseTransaction(n));
        }
        return out;
    }

    /** Exclui um lancamento pertencente ao usuario atual. */
    public void deleteById(String id) throws Exception {
        String encoded = URLEncoder.encode("eq." + id, StandardCharsets.UTF_8);
        String url = restBase() + "?id=" + encoded + "&user_id=eq." + encode(Session.userId());
        client.delete(url);
    }

    /** Envia um comprovante e retorna o caminho salvo no armazenamento. */
    public String uploadReceipt(String transactionId, Path file) throws Exception {
        if (file == null) {
            return null;
        }
        String fileName = file.getFileName().toString();
        String ext = "";
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0 && dot < fileName.length() - 1) {
            ext = "." + fileName.substring(dot + 1);
        }
        String storagePath = Session.userId() + "/" + transactionId + "/comprovante" + ext;
        String url = AppConfig.SUPABASE_URL + "/storage/v1/object/receipts/" + storagePath;
        String contentType = URLConnection.guessContentTypeFromName(fileName);
        try {
            client.upload(url, Files.readAllBytes(file), contentType, true);
        } catch (RuntimeException ex) {
            String message = ex.getMessage();
            if (message != null && message.contains("row-level security")) {
                throw new RuntimeException("O Supabase bloqueou o upload do comprovante pela politica RLS do bucket receipts. Aplique o arquivo supabase-storage-policies.sql no SQL Editor do Supabase.");
            }
            throw ex;
        }
        return storagePath;
    }

    /** Solicita uma URL temporaria para acessar o comprovante. */
    public String createReceiptUrl(String receiptPath) throws Exception {
        String body = "{\"expiresIn\":60}";
        String url = AppConfig.SUPABASE_URL + "/storage/v1/object/sign/receipts/" + receiptPath;
        JsonNode n = mapper.readTree(client.postJson(url, body, null));
        String signed = n.path("signedURL").asText(n.path("signedUrl").asText(null));
        if (signed == null || signed.isBlank()) {
            throw new RuntimeException("Supabase nao retornou URL assinada.");
        }
        if (signed.startsWith("http")) {
            return signed;
        }
        return AppConfig.SUPABASE_URL + "/storage/v1" + signed;
    }

    /** Converte o JSON retornado pelo Supabase em um lancamento. */
    private Transaction parseTransaction(JsonNode n) {
        Transaction t = new Transaction();

        t.setId(n.path("id").asText(null));
        t.setUserId(n.path("user_id").asText(null));
        t.setType(TxType.valueOf(n.path("type").asText("EXPENSE")));
        t.setAmount(new BigDecimal(n.path("amount").asText("0")));
        t.setCategory(n.path("category").asText("Outros"));
        t.setDescription(n.path("description").isNull() ? "" : n.path("description").asText(""));
        t.setDate(LocalDate.parse(n.path("date").asText(LocalDate.now().toString())));
        t.setReceiptPath(n.path("receipt_path").isNull() ? null : n.path("receipt_path").asText(null));

        t.setCreated_at(
                java.time.OffsetDateTime
                        .parse(n.path("created_at").asText())
                        .atZoneSameInstant(java.time.ZoneId.systemDefault())
                        .toLocalDateTime()

        );

        return t;
    }

    /** Escapa caracteres especiais antes de montar um JSON. */
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Codifica um valor para uso seguro em parametros de URL. */
    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
