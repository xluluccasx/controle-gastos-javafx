/** Processa o retorno enviado pelo Supabase para confirmar o acesso do usuario. */
import { supabase } from "./supabase.js";

const statusEl = document.getElementById("confirmStatus");
const loginUrl = new URL("index.html", window.location.href);

confirmAuthRedirect();

/** Confirma o codigo ou token recebido no redirecionamento. */
async function confirmAuthRedirect() {
  setStatus("Confirmando acesso...");

  try {
    const params = new URLSearchParams(window.location.search);
    const code = params.get("code");
    const token = params.get("token") || params.get("token_hash");
    const type = params.get("type") || "email";

    if (code) {
      const { error } = await supabase.auth.exchangeCodeForSession(code);
      if (error) throw error;
    } else if (token) {
      const { error } = await supabase.auth.verifyOtp({
        token_hash: token,
        type
      });

      if (error) throw error;
    } else {
      throw new Error("Link de confirmacao invalido ou incompleto.");
    }

    const { error } = await supabase.auth.getSession();
    if (error) throw error;

    setStatus("Email confirmado. Redirecionando para o login...");
    window.location.replace(loginUrl.href);
  } catch (error) {
    console.error(error);
    setStatus(`Nao foi possivel confirmar o acesso: ${error.message}`);
  }
}

/** Atualiza a mensagem exibida na tela de confirmacao. */
function setStatus(message) {
  if (statusEl) {
    statusEl.textContent = message;
  }
}
