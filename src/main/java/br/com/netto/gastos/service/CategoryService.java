package br.com.netto.gastos.service;

import br.com.netto.gastos.config.AppConfig;
import br.com.netto.gastos.config.Session;
import br.com.netto.gastos.model.CategoryItem;
import br.com.netto.gastos.model.TxType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gerencia categorias globais, personalizadas e preferencias do usuario no Supabase.
 */
public class CategoryService {
    private final SupabaseClient client = new SupabaseClient();
    private final ObjectMapper mapper = new ObjectMapper();

    /** Monta o endereco REST da tabela informada. */
    private String table(String name) {
        return AppConfig.SUPABASE_URL + "/rest/v1/" + name;
    }

    /** Lista as categorias visiveis do tipo de lancamento informado. */
    public List<CategoryItem> listVisibleByType(TxType type) throws Exception {
        List<CategoryItem> all = listForCurrentUser();
        return all.stream()
                .filter(c -> c.getType() == type)
                .filter(c -> !c.isHidden())
                .toList();
    }

    /** Combina categorias padrao e personalizadas com as preferencias do usuario. */
    public List<CategoryItem> listForCurrentUser() throws Exception {
        JsonNode categories = mapper.readTree(client.get(table("categories") + "?select=*&order=name.asc"));
        JsonNode userCategories = mapper.readTree(client.get(
                table("user_categories") + "?select=*&user_id=eq." + encode(Session.userId())
        ));

        Map<String, JsonNode> userConfig = new HashMap<>();
        if (userCategories.isArray()) {
            for (JsonNode n : userCategories) {
                userConfig.put(n.path("category_id").asText(), n);
            }
        }

        List<CategoryItem> out = new ArrayList<>();
        if (!categories.isArray()) {
            return out;
        }

        for (JsonNode n : categories) {
            boolean isDefault = n.path("is_default").asBoolean(false);
            JsonNode cfg = userConfig.get(n.path("id").asText());
            boolean isOwnCustom = !isDefault && cfg != null;
            if (!isDefault && !isOwnCustom) {
                continue;
            }

            CategoryItem item = new CategoryItem();
            item.setId(n.path("id").asText());
            item.setName(n.path("name").asText());
            item.setType(TxType.valueOf(n.path("type").asText("EXPENSE")));
            item.setDefaultCategory(isDefault);
            item.setHidden(cfg != null && cfg.path("hidden").asBoolean(false));
            item.setOwnedByUser(isOwnCustom);
            out.add(item);
        }
        return out;
    }

    /** Cria uma categoria personalizada e a vincula ao usuario. */
    public void add(String name, TxType type) throws Exception {
        String body = """
                {"name":"%s","type":"%s","is_default":false}
                """.formatted(escapeJson(name), type.name());
        String json = client.postJson(table("categories"), body, "return=representation");
        JsonNode arr = mapper.readTree(json);
        if (!arr.isArray() || arr.isEmpty()) {
            throw new RuntimeException("Categoria criada, mas sem retorno do Supabase.");
        }
        String categoryId = arr.get(0).path("id").asText();
        link(categoryId, false);
    }

    /** Altera o nome de uma categoria personalizada. */
    public void rename(String categoryId, String newName) throws Exception {
        String body = "{\"name\":\"" + escapeJson(newName) + "\"}";
        client.patchJson(table("categories") + "?id=eq." + encode(categoryId) + "&is_default=eq.false", body, null);
    }

    /** Remove o vinculo e a categoria personalizada. */
    public void deleteCustom(String categoryId) throws Exception {
        client.delete(table("user_categories")
                + "?user_id=eq." + encode(Session.userId())
                + "&category_id=eq." + encode(categoryId));
        client.delete(table("categories") + "?id=eq." + encode(categoryId) + "&is_default=eq.false");
    }

    /** Define se a categoria esta oculta. */
    public void setHidden(String categoryId, boolean hidden) throws Exception {
        String body = """
                {"user_id":"%s","category_id":"%s","hidden":%s}
                """.formatted(Session.userId(), categoryId, hidden);
        client.postJson(
                table("user_categories") + "?on_conflict=user_id,category_id",
                body,
                "resolution=merge-duplicates"
        );
    }

    /** Vincula uma categoria personalizada ao usuario atual. */
    private void link(String categoryId, boolean hidden) throws Exception {
        String body = """
                {"user_id":"%s","category_id":"%s","hidden":%s}
                """.formatted(Session.userId(), categoryId, hidden);
        client.postJson(table("user_categories"), body, null);
    }

    /** Codifica um valor para uso seguro em parametros de URL. */
    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Escapa caracteres especiais antes de montar um JSON. */
    private static String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
