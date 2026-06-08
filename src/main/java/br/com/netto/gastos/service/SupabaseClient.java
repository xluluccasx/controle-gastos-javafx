package br.com.netto.gastos.service;

import br.com.netto.gastos.config.AppConfig;
import br.com.netto.gastos.config.Session;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Encapsula requisicoes HTTP autenticadas para as APIs REST e Auth do Supabase.
 */
public class SupabaseClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /** Cria uma requisicao HTTP com os cabecalhos comuns de autenticacao. */
    private HttpRequest.Builder baseRequest(String url) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(25))
                .header("apikey", AppConfig.SUPABASE_PUBLISHABLE_KEY);

        if (Session.isLoggedIn()) {
            b.header("Authorization", "Bearer " + Session.accessToken());
        } else {
            b.header("Authorization", "Bearer " + AppConfig.SUPABASE_PUBLISHABLE_KEY);
        }

        return b;
    }

    /** Envia uma requisicao POST com corpo JSON e valida a resposta. */
    public String postJson(String url, String json, String preferHeader) throws Exception {
        HttpRequest.Builder b = baseRequest(url)
                .header("Content-Type", "application/json");

        if (preferHeader != null && !preferHeader.isBlank()) {
            b.header("Prefer", preferHeader);
        }

        HttpRequest req = b.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        System.out.println("POST URL: " + url);
        System.out.println("STATUS: " + res.statusCode());
        System.out.println("BODY: " + res.body());

        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new RuntimeException("Erro HTTP " + res.statusCode() + ": " + res.body());
        }
        return res.body();
    }

    /** Envia uma requisicao PATCH com corpo JSON e valida a resposta. */
    public String patchJson(String url, String json, String preferHeader) throws Exception {
        HttpRequest.Builder b = baseRequest(url)
                .header("Content-Type", "application/json");

        if (preferHeader != null && !preferHeader.isBlank()) {
            b.header("Prefer", preferHeader);
        }

        HttpRequest req = b.method("PATCH", HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        System.out.println("PATCH URL: " + url);
        System.out.println("STATUS: " + res.statusCode());
        System.out.println("BODY: " + res.body());

        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new RuntimeException("Erro HTTP " + res.statusCode() + ": " + res.body());
        }
        return res.body();
    }

    /** Executa uma consulta GET e retorna o corpo da resposta. */
    public String get(String url) throws Exception {
        HttpRequest req = baseRequest(url).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        System.out.println("GET URL: " + url);
        System.out.println("STATUS: " + res.statusCode());
        System.out.println("BODY: " + res.body());

        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new RuntimeException("Erro HTTP " + res.statusCode() + ": " + res.body());
        }
        return res.body();
    }

    /** Executa uma requisicao DELETE e retorna o corpo da resposta. */
    public String delete(String url) throws Exception {
        HttpRequest req = baseRequest(url).DELETE().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        System.out.println("DELETE URL: " + url);
        System.out.println("STATUS: " + res.statusCode());
        System.out.println("BODY: " + res.body());

        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new RuntimeException("Erro HTTP " + res.statusCode() + ": " + res.body());
        }
        return res.body();
    }

    /** Envia um arquivo binario ao armazenamento e valida a resposta. */
    public String upload(String url, byte[] bytes, String contentType, boolean upsert) throws Exception {
        HttpRequest req = baseRequest(url)
                .header("Content-Type", contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType)
                .header("x-upsert", String.valueOf(upsert))
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        System.out.println("UPLOAD URL: " + url);
        System.out.println("STATUS: " + res.statusCode());
        System.out.println("BODY: " + res.body());

        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new RuntimeException("Erro HTTP " + res.statusCode() + ": " + res.body());
        }
        return res.body();
    }
}
