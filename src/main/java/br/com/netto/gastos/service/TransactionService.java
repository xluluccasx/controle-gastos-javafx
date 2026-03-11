package br.com.netto.gastos.service;

import br.com.netto.gastos.config.AppConfig;
import br.com.netto.gastos.config.Session;
import br.com.netto.gastos.model.Category;
import br.com.netto.gastos.model.Transaction;
import br.com.netto.gastos.model.TxType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TransactionService {

    private final SupabaseClient client = new SupabaseClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private String restBase() {
        return AppConfig.SUPABASE_URL + "/rest/v1/" + AppConfig.TABLE_TRANSACTIONS;
    }

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
                  "date": "%s",
                  "created_at": "%s"
                }
                """.formatted(
                t.getUserId(),
                t.getType().name(),
                t.getAmount().toPlainString(),
                t.getCategory().name(),
                (t.getDescription() == null || t.getDescription().isBlank())
                        ? "null"
                        : "\"" + escapeJson(t.getDescription()) + "\"",
                t.getDate().toString(),
                t.getCreated_at()
        );

        // return=representation faz o Supabase devolver a linha criada
        String json = client.postJson(restBase(), body, "return=representation");
        JsonNode arr = mapper.readTree(json);
        if (!arr.isArray() || arr.size() == 0) return t;

        return parseTransaction(arr.get(0));
    }

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

    public void deleteById(String id) throws Exception {
        String encoded = URLEncoder.encode("eq." + id, StandardCharsets.UTF_8);
        // filtro: id=eq.<uuid>
        String url = restBase() + "?id=" + encoded;
        client.delete(url);
    }

    private Transaction parseTransaction(JsonNode n) {
        Transaction t = new Transaction();

        t.setId(n.path("id").asText(null));
        t.setUserId(n.path("user_id").asText(null));
        t.setType(TxType.valueOf(n.path("type").asText("EXPENSE")));
        t.setAmount(new BigDecimal(n.path("amount").asText("0")));
        t.setCategory(Category.valueOf(n.path("category").asText("OUTROS")));
        t.setDescription(n.path("description").isNull() ? "" : n.path("description").asText(""));
        t.setDate(LocalDate.parse(n.path("date").asText(LocalDate.now().toString())));

        t.setCreated_at(
                java.time.OffsetDateTime
                        .parse(n.path("created_at").asText())
                        .atZoneSameInstant(java.time.ZoneId.systemDefault())
                        .toLocalDateTime()

        );

        return t;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}