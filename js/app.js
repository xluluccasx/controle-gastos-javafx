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
const startDateEl = document.getElementById("startDate");
const endDateEl = document.getElementById("endDate");
const btnApplyFilter = document.getElementById("btnApplyFilter");

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
  if (startDateEl && endDateEl) {
    const now = new Date();
    const firstDay = new Date(now.getFullYear(), now.getMonth(), 1);

    startDateEl.value = firstDay.toISOString().slice(0, 10);
    endDateEl.value = now.toISOString().slice(0, 10);
  }
  dateEl.value = todayISO();

  await applySession();

  btnLogin.addEventListener("click", onLogin);
  btnSignup.addEventListener("click", onSignup);
  btnForgot.addEventListener("click", onForgotPassword);
  btnLogout.addEventListener("click", onLogout);
  btnRefresh.addEventListener("click", loadTransactions);
  btnAddTransaction.addEventListener("click", onAddTransaction);
  btnApplyFilter.addEventListener("click", () => {
    animateRangeChange(loadTransactions);
  });

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

    requestAnimationFrame(() => {
      animateDashboard();
    });

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
    const { data, error } = await supabase.auth.signInWithPassword({
      email,
      password
    });

    if (error) {
      setAuthError(error.message);
      return;
    }

    console.log("login ok");

    // 🔥 FORÇA UI (não depende do Supabase)
    authSection.classList.add("hidden");
    dashboardSection.classList.remove("hidden");
    userBox.classList.remove("hidden");
    userEmail.textContent = data.user.email;

    await loadTransactions();

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
  console.log("Button Pressed");

  try {
    supabase.auth.signOut().catch(() => {});

    Object.keys(localStorage).forEach(key => {
      if (key.startsWith("sb-")) {
        localStorage.removeItem(key);
      }
    });

    sessionStorage.clear();

    console.log("sessão limpa manualmente");

    animateLogout();

  } catch (err) {
    console.error(err);
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

  const start = startDateEl.value;
  const end = endDateEl.value;

  let query = supabase
    .from(TABLE_NAME)
    .select("*")
    .eq("user_id", session.user.id)
    .order("date", { ascending: false });

  if (start) {
    query = query.gte("date", start);
  }

  if (end) {
    query = query.lte("date", end);
  }

  const { data, error } = await query;

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

}
transactionsTable.addEventListener("click", async (e) => {

  const btnDelete = e.target.closest(".action-delete");
  if (btnDelete) {
    const id = btnDelete.dataset.id;

    console.log("clicou delete:", id);

    if (!confirm("Deseja excluir este lançamento?")) return;

    await deleteTransaction(id);
    return;
  }

  const btnView = e.target.closest(".action-view");
  if (btnView) {
    const path = btnView.dataset.path;

    await openReceipt(path);
    return;
  }
});

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

function animateLogout() {
  const dashboard = document.getElementById("dashboardSection");
  const auth = document.getElementById("authSection");

  // aplica fade-out no dashboard
  dashboard.classList.add("fade-out");

  setTimeout(() => {
    // esconde dashboard
    dashboard.classList.add("hidden");
    dashboard.classList.remove("fade-out");

    // mostra login
    auth.classList.remove("hidden");

    // anima entrada
    auth.classList.add("fade-in");

    setTimeout(() => {
      auth.classList.remove("fade-in");
    }, 300);

  }, 300);
}

function animateDashboard() {
  const items = document.querySelectorAll("#dashboardSection .bubble");

  items.forEach((el, index) => {
    setTimeout(() => {
      el.classList.add("show");
    }, index * 120);
  });
}

document.addEventListener("click", function (e) {
  const btn = e.target.closest(".btn");
  if (!btn) return;

  const circle = document.createElement("span");
  circle.classList.add("ripple");

  const rect = btn.getBoundingClientRect();

  const size = Math.max(rect.width, rect.height);
  const x = e.clientX - rect.left - size / 2;
  const y = e.clientY - rect.top - size / 2;

  circle.style.width = circle.style.height = size + "px";
  circle.style.left = x + "px";
  circle.style.top = y + "px";

  btn.appendChild(circle);

  setTimeout(() => {
    circle.remove();
  }, 600);
});
async function animateRangeChange(callback) {
  const content = document.querySelectorAll(
    ".content-grid, .kpis, .table-wrap"
  );

  content.forEach(el => el.classList.add("range-transition-out"));

  await new Promise(r => setTimeout(r, 250));

  await callback();

  content.forEach(el => {
    el.classList.remove("range-transition-out");
    el.classList.add("range-transition-in");
  });

  setTimeout(() => {
    content.forEach(el => el.classList.remove("range-transition-in"));
  }, 250);
}