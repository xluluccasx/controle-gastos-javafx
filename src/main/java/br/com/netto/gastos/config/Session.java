package br.com.netto.gastos.config;

/**
 * Mantem em memoria os dados da sessao do usuario autenticado.
 */
public final class Session {
    /** Impede a criacao de instancias desta classe de sessao. */
    private Session() {}

    private static String accessToken;
    private static String userId;
    private static String email;

    /** Informa se existe um token de acesso valido armazenado. */
    public static boolean isLoggedIn() {
        return accessToken != null && !accessToken.isBlank();
    }

    /** Armazena os dados retornados por um login bem-sucedido. */
    public static void setLogin(String token, String userIdValue, String emailValue) {
        accessToken = token;
        userId = userIdValue;
        email = emailValue;
    }

    /** Remove todos os dados da sessao atual. */
    public static void clear() {
        accessToken = null;
        userId = null;
        email = null;
    }

    /** Retorna o token de acesso da sessao. */
    public static String accessToken() { return accessToken; }
    /** Retorna o identificador do usuario autenticado. */
    public static String userId() { return userId; }
    /** Retorna o email do usuario autenticado. */
    public static String email() { return email; }
}
