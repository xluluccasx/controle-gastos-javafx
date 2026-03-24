import { supabase } from "./supabase.js";

export async function signUp(email, password) {
  const { data, error } = await supabase.auth.signUp({
    email,
    password
  });

  if (error) throw new Error(mapAuthError(error));
  return data;
}

export async function signIn(email, password) {
  const { data, error } = await supabase.auth.signInWithPassword({
    email,
    password
  });

  if (error) throw new Error(mapAuthError(error));
  return data;
}

export async function signOut() {
  const { error } = await supabase.auth.signOut();
  if (error) throw new Error(error.message);
}

export async function getSession() {
  const { data, error } = await supabase.auth.getSession();
  if (error) throw new Error(error.message);
  return data.session;
}

export async function forgotPassword(email) {
  const { error } = await supabase.auth.resetPasswordForEmail(email);
  if (error) throw new Error(mapAuthError(error));
}

function mapAuthError(error) {
  const msg = error?.message || "";

  if (msg.includes("Email not confirmed")) {
    return "Seu email ainda não foi confirmado.";
  }

  if (msg.includes("Invalid login credentials")) {
    return "Email ou senha inválidos.";
  }

  if (msg.includes("User already registered")) {
    return "Este email já está cadastrado.";
  }

  return msg || "Erro de autenticação.";
}