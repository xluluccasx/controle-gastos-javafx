package br.com.netto.gastos.config;

public final class Session {
    private Session() {}

    private static String accessToken;
    private static String userId;
    private static String email;

    public static boolean isLoggedIn() {
        return accessToken != null && !accessToken.isBlank();
    }

    public static void setLogin(String token, String userIdValue, String emailValue) {
        accessToken = token;
        userId = userIdValue;
        email = emailValue;
    }

    public static void clear() {
        accessToken = null;
        userId = null;
        email = null;
    }

    public static String accessToken() { return accessToken; }
    public static String userId() { return userId; }
    public static String email() { return email; }
}