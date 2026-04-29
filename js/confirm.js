import { supabase } from "./supabase.js";

const statusEl = document.getElementById("confirmStatus");
const loginUrl = new URL("index.html", window.location.href);

confirmAuthRedirect();

async function confirmAuthRedirect() {
  setStatus("Confirmando acesso...");

  try {
    const code = new URLSearchParams(window.location.search).get("code");

    if (code) {
      const { error } = await supabase.auth.exchangeCodeForSession(code);
      if (error) throw error;
    }

    const { data, error } = await supabase.auth.getSession();
    if (error) throw error;

    if (data.session) {
      setStatus("Acesso confirmado. Redirecionando...");
      window.location.replace(loginUrl.href);
      return;
    }

    setStatus("Link confirmado. Volte para a tela de login.");
    window.location.replace(loginUrl.href);
  } catch (error) {
    console.error(error);
    setStatus(`Nao foi possivel confirmar o acesso: ${error.message}`);
  }
}

function setStatus(message) {
  if (statusEl) {
    statusEl.textContent = message;
  }
}
