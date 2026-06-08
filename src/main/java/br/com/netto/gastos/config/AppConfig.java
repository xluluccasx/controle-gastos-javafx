package br.com.netto.gastos.config;

/**
 * Centraliza os enderecos, chaves publicas e nomes de tabelas usados pela aplicacao.
 */
public final class AppConfig {
    /** Impede a criacao de instancias desta classe de configuracao. */
    private AppConfig() {}

    public static final String SUPABASE_URL = "https://ozotymqcrvjjbskbwmdk.supabase.co";
    public static final String SUPABASE_PUBLISHABLE_KEY = "sb_publishable_VfSrGASQ5d15o_4szbK8DA_ILkKYior";

    public static final String TABLE_TRANSACTIONS = "transactions";
}
