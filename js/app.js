import { supabase } from "./supabase.js";
import { signIn, signOut, signUp, forgotPassword, getSession } from "./auth.js";
import { TABLE_NAME } from "./config.js";

const authSection = document.getElementById("authSection");
const dashboardSection = document.getElementById("dashboardSection");
const authStatus = document.getElementById("authStatus");
const txStatus = document.getElementById("txStatus");
const userBox = document.getElementById("userBox");
const userEmail = document.getElementById("userEmail");

const emailEl = document.getElementById("email");
const passwordEl = document.getElementById("password");

const btnLogin = document.getElementById("btnLogin");
const btnSignup = document.getElementById("btnSignup");
const btnForgot = document.getElementById("btnForgot");
const btnLogout = document.getElementById("btnLogout");
const btnRefresh = document.getElementById("btnRefresh");
const btnAddTransaction = document.getElementById("btnAddTransaction");

const monthFilter = document.getElementById("monthFilter");

const typeEl = document.getElementById("type");
const categoryEl = document.getElementById("category");
const amountEl = document.getElementById("amount");
const dateEl = document.getElementById("date");
const descriptionEl = document.getElementById("description");
const receiptFileEl = document.getElementById("receiptFile");
const btnExportPdf = document.getElementById("btnExportPdf");
btnExportPdf.addEventListener("click", exportPdf);

const kpiIncome = document.getElementById("kpiIncome");
const kpiExpense = document.getElementById("kpiExpense");
const kpiBalance = document.getElementById("kpiBalance");
const transactionsTable = document.getElementById("transactionsTable");

let pieChart;
let barChart;
let currentTransactions = [];

const categoryLabels = {
  ALIMENTACAO: "Alimentação",
  TRANSPORTE: "Transporte",
  MORADIA: "Moradia",
  SAUDE: "Saúde",
  EDUCACAO: "Educação",
  LAZER: "Lazer",
  CONTAS: "Contas",
  OUTROS: "Outros"
};

const typeLabels = {
  INCOME: "Receita",
  EXPENSE: "Despesa"
};

init();

async function init() {
  const now = new Date();
  monthFilter.value = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
  dateEl.value = todayISO();

  await applySession();

  btnLogin.addEventListener("click", onLogin);
  btnSignup.addEventListener("click", onSignup);
  btnForgot.addEventListener("click", onForgotPassword);
  btnLogout.addEventListener("click", onLogout);
  btnRefresh.addEventListener("click", loadTransactions);
  btnAddTransaction.addEventListener("click", onAddTransaction);

  supabase.auth.onAuthStateChange(async () => {
    await applySession();
  });
}

async function applySession() {
  clearStatus();
  const session = await getSession();

  if (session?.user) {
    authSection.classList.add("hidden");
    dashboardSection.classList.remove("hidden");
    userBox.classList.remove("hidden");
    userEmail.textContent = session.user.email || "";
    await loadTransactions();
  } else {
    authSection.classList.remove("hidden");
    dashboardSection.classList.add("hidden");
    userBox.classList.add("hidden");
    userEmail.textContent = "";
  }
}

async function onLogin() {
  clearStatus();

  const email = emailEl.value.trim();
  const password = passwordEl.value.trim();

  if (!email || !password) {
    setAuthError("Preencha email e senha.");
    return;
  }

  try {
    await signIn(email, password);
  } catch (err) {
    setAuthError(`Falha no login: ${err.message}`);
  }
}

async function onSignup() {
  clearStatus();

  const email = emailEl.value.trim();
  const password = passwordEl.value.trim();

  if (!email || !password) {
    setAuthError("Preencha email e senha.");
    return;
  }

  try {
    await signUp(email, password);
    setAuthSuccess(`Conta criada.\nVerifique o email enviado para: ${email}`);
  } catch (err) {
    setAuthError(`Falha ao criar conta: ${err.message}`);
  }
}

async function onForgotPassword() {
  clearStatus();

  const email = emailEl.value.trim();

  if (!email) {
    setAuthError("Informe o email para recuperar a senha.");
    return;
  }

  try {
    await forgotPassword(email);
    setAuthSuccess(`Enviamos um link de redefinição para: ${email}`);
  } catch (err) {
    setAuthError(err.message);
  }
}

async function onLogout() {
  try {
    await signOut();
    window.location.reload(); // ✔ reload em vez de redirect
  } catch (err) {
    console.error("Erro ao sair:", err);
  }
}

async function onAddTransaction() {
  txStatus.textContent = "";
  txStatus.style.color = "#b00020";

  const session = await getSession();
  if (!session?.user) return;

  const amount = Number(amountEl.value);
  const date = dateEl.value;

  if (!amount || amount <= 0 || !date) {
    setTxError("Informe valor e data válidos.");
    return;
  }

  const payload = {
    user_id: session.user.id,
    type: typeEl.value,
    amount,
    category: categoryEl.value,
    description: descriptionEl.value.trim() || null,
    date
  };

  const { data, error } = await supabase
    .from(TABLE_NAME)
    .insert(payload)
    .select()
    .single();

  if (error) {
    setTxError(`Erro ao salvar: ${error.message}`);
    return;
  }

  const file = document.getElementById("receiptFile")?.files[0];

  if (file) {
    const ext = file.name.split(".").pop();
    const filePath = `${session.user.id}/${data.id}/comprovante.${ext}`;

    const { error: uploadError } = await supabase.storage
      .from("receipts")
      .upload(filePath, file, {
        cacheControl: "3600",
        upsert: true
      });

    if (uploadError) {
      setTxError("Salvou, mas erro no comprovante: " + uploadError.message);
      return;
    }

    const { error: updateError } = await supabase
      .from(TABLE_NAME)
      .update({ receipt_path: filePath })
      .eq("id", data.id);

    if (updateError) {
      setTxError("Upload feito, mas erro ao vincular: " + updateError.message);
      return;
    }
  }

  setTxSuccess("Lançamento salvo!");

  amountEl.value = "";
  descriptionEl.value = "";
  dateEl.value = new Date().toISOString().slice(0, 10);

  const fileInput = document.getElementById("receiptFile");
  if (fileInput) fileInput.value = "";

  await loadTransactions();
}

async function openReceipt(path) {
  const { data, error } = await supabase.storage
    .from("receipts")
    .createSignedUrl(path, 60);

  if (error) {
    alert("Erro ao abrir comprovante: " + error.message);
    return;
  }

  window.open(data.signedUrl, "_blank");
}

async function loadTransactions() {
  const session = await getSession();
  if (!session?.user) return;

  const [year, month] = monthFilter.value.split("-").map(Number);
  const from = `${year}-${String(month).padStart(2, "0")}-01`;
  const to = endOfMonthISO(year, month);

  const { data, error } = await supabase
    .from(TABLE_NAME)
    .select("*")
    .eq("user_id", session.user.id)
    .gte("date", from)
    .lte("date", to)
    .order("date", { ascending: false });

  if (error) {
    setTxError(`Erro ao carregar: ${error.message}`);
    return;
  }

  renderTable(data || []);
  currentTransactions = data || [];
  updateKpisAndCharts(data || []);
}

function renderTable(list) {
  transactionsTable.innerHTML = "";

  for (const tx of list) {
    const tr = document.createElement("tr");

    tr.innerHTML = `
      <td>${formatDate(tx.date)}</td>
      <td>${typeLabels[tx.type] || tx.type}</td>
      <td>${categoryLabels[tx.category] || tx.category}</td>
      <td>${formatMoney(Number(tx.amount))}</td>
      <td>${tx.description ?? ""}</td>
      <td>${formatDateTimeLocal(tx.created_at)}</td>
      <td>
        <button class="btn btn-danger action-delete" data-id="${tx.id}">Excluir</button>

        ${
          tx.receipt_path
            ? `<button class="btn btn-light action-view" data-path="${tx.receipt_path}">Ver</button>`
            : ""
        }
      </td>
    `;

    transactionsTable.appendChild(tr);
  }

  document.querySelectorAll(".action-delete").forEach(btn => {
    btn.addEventListener("click", async (e) => {
      const id = e.target.dataset.id;

      if (!confirm("Deseja excluir este lançamento?")) return;

      await deleteTransaction(id);
    });
  });

  document.querySelectorAll(".action-view").forEach(btn => {
    btn.addEventListener("click", async (e) => {
      const path = e.target.dataset.path;
      await openReceipt(path);
    });
  });
}

async function deleteTransaction(id) {
  // 1. buscar o receipt_path antes de deletar
  const { data: tx, error: fetchError } = await supabase
    .from(TABLE_NAME)
    .select("receipt_path")
    .eq("id", id)
    .single();

  if (fetchError) {
    setTxError("Erro ao buscar comprovante: " + fetchError.message);
    return;
  }

  // 2. apagar arquivo do storage (se existir)
  if (tx?.receipt_path) {
    const { error: storageError } = await supabase.storage
      .from("receipts")
      .remove([tx.receipt_path]);

    if (storageError) {
      console.warn("Erro ao apagar comprovante:", storageError.message);
    }
  }

  // 3. apagar do banco
  const { error } = await supabase
    .from(TABLE_NAME)
    .delete()
    .eq("id", id);

  if (error) {
    setTxError(`Erro ao excluir: ${error.message}`);
    return;
  }

  await loadTransactions();
}

function updateKpisAndCharts(list) {
  let income = 0;
  let expense = 0;

  const expenseByCategory = {
    ALIMENTACAO: 0,
    TRANSPORTE: 0,
    MORADIA: 0,
    SAUDE: 0,
    EDUCACAO: 0,
    LAZER: 0,
    CONTAS: 0,
    OUTROS: 0
  };

  for (const tx of list) {
    const value = Number(tx.amount || 0);
    if (tx.type === "INCOME") {
      income += value;
    } else {
      expense += value;
      expenseByCategory[tx.category] = (expenseByCategory[tx.category] || 0) + value;
    }
  }

  const balance = income - expense;

  kpiIncome.textContent = formatMoney(income);
  kpiExpense.textContent = formatMoney(expense);
  kpiBalance.textContent = formatMoney(balance);

  renderPieChart(expenseByCategory);
  renderBarChart(income, expense);
}

function renderPieChart(expenseByCategory) {
  const labels = [];
  const data = [];

  for (const [key, value] of Object.entries(expenseByCategory)) {
    if (value > 0) {
      labels.push(categoryLabels[key] || key);
      data.push(value);
    }
  }

  if (pieChart) pieChart.destroy();

  pieChart = new Chart(document.getElementById("pieChart"), {
    type: "pie",
    data: {
      labels,
      datasets: [{
        data
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        title: {
          display: true,
          text: "Gastos por Categoria"
        }
      }
    }
  });
}

function renderBarChart(income, expense) {
  if (barChart) barChart.destroy();

  barChart = new Chart(document.getElementById("barChart"), {
    type: "bar",
    data: {
      labels: ["Receita", "Despesa"],
      datasets: [{
        label: "Total no mês",
        data: [income, expense],
        backgroundColor: ["#2ecc71", "#e74c3c"]
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        title: {
          display: true,
          text: "Totais por Tipo"
        }
      }
    }
  });
}

function formatMoney(value) {
  return value.toLocaleString("pt-BR", {
    style: "currency",
    currency: "BRL"
  });
}

function formatDate(isoDate) {
  if (!isoDate) return "";
  const [year, month, day] = isoDate.split("-");
  return `${day}/${month}/${year}`;
}

function formatDateTimeLocal(isoDateTime) {
  if (!isoDateTime) return "";

  const d = new Date(isoDateTime);
  return d.toLocaleString("pt-BR", {
    timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone
  });
}

function endOfMonthISO(year, month) {
  const date = new Date(year, month, 0);
  return date.toISOString().slice(0, 10);
}

function todayISO() {
  const d = new Date();
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
}

function clearStatus() {
  authStatus.textContent = "";
  txStatus.textContent = "";
}

function setAuthError(msg) {
  authStatus.style.color = "#b00020";
  authStatus.textContent = msg;
}

function setAuthSuccess(msg) {
  authStatus.style.color = "#0b6b2b";
  authStatus.textContent = msg;
}

function setTxError(msg) {
  txStatus.style.color = "#b00020";
  txStatus.textContent = msg;
}

function setTxSuccess(msg) {
  txStatus.style.color = "#0b6b2b";
  txStatus.textContent = msg;
}

function exportPdf() {
  const { jsPDF } = window.jspdf;
  const doc = new jsPDF();

  const income = currentTransactions
    .filter(t => t.type === "INCOME")
    .reduce((sum, t) => sum + Number(t.amount || 0), 0);

  const expense = currentTransactions
    .filter(t => t.type === "EXPENSE")
    .reduce((sum, t) => sum + Number(t.amount || 0), 0);

  doc.setFontSize(16);
  doc.text("Relatório de Controle de Gastos", 14, 18);

  doc.setFontSize(11);
  doc.text(`Mês: ${monthFilter.value}`, 14, 28);
  doc.text(`Receitas: ${formatMoney(income)}`, 14, 36);
  doc.text(`Despesas: ${formatMoney(expense)}`, 14, 44);
  doc.text(`Saldo: ${formatMoney(income - expense)}`, 14, 52);

  doc.autoTable({
    startY: 62,
    head: [["Data", "Tipo", "Categoria", "Valor", "Descrição"]],
    body: currentTransactions.map(t => [
      formatDate(t.date),
      typeLabels[t.type] || t.type,
      categoryLabels[t.category] || t.category,
      formatMoney(Number(t.amount)),
      t.description || ""
    ])
  });

  doc.save(`relatorio-gastos-${monthFilter.value}.pdf`);
}