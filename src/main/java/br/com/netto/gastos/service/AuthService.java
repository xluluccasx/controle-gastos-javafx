package br.com.netto.gastos.service;

import br.com.netto.gastos.config.AppConfig;
import br.com.netto.gastos.config.Session;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AuthService {

    private final SupabaseClient client = new SupabaseClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public void signUp(String email, String password) throws Exception {
        String url = AppConfig.SUPABASE_URL + "/auth/v1/signup";

        String body = """
            {"email":"%s","password":"%s"}
            """.formatted(email, password);

        try {

            client.postJson(url, body, null);

        } catch (Exception e) {

            String message = e.getMessage();

            if (message != null && message.contains("email_not_confirmed")) {
                throw new RuntimeException("Seu email ainda não foi confirmado.");
            }

            if (message != null && message.contains("invalid_credentials")) {
                throw new RuntimeException("email ou senha invalidos.");
            }

            if (message != null && message.contains("User already registered")) {
                throw new RuntimeException("Este email já está cadastrado.");
            }

            throw new RuntimeException("Erro ao criar usuário: " + message);
        }
    }

    public void signIn(String email, String password) throws Exception {
        String url = AppConfig.SUPABASE_URL + "/auth/v1/token?grant_type=password";

        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);

        try {
            String json = client.postJson(url, body, null);

            JsonNode root = mapper.readTree(json);
            String accessToken = root.path("access_token").asText(null);
            String userId = root.path("user").path("id").asText(null);

            if (accessToken == null || userId == null) {
                throw new RuntimeException("Login não retornou token/usuário. Resposta: " + json);
            }

            Session.setLogin(accessToken, userId, email);

        } catch (RuntimeException e) {
            String message = e.getMessage();

            if (message != null && message.contains("email_not_confirmed")) {
                throw new RuntimeException("Seu email ainda não foi confirmado.");
            }

            if (message != null && message.contains("invalid_credentials")) {
                throw new RuntimeException("Email ou senha inválidos.");
            }

            throw new RuntimeException("Falha no login: " + message);
        }
    }

    public void signOut() {
        Session.clear();
    }
}