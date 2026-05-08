import { supabase } from "./supabase.js";
import { signUp, forgotPassword, getSession } from "./auth.js";
import { TABLE_NAME } from "./config.js";

const SESSION_TIMEOUT_MS = 30 * 60 * 1000;
const LAST_ACTIVITY_KEY = "controle-gastos:lastActivityAt";
const ACTIVITY_EVENTS = ["click", "keydown", "mousemove", "scroll", "touchstart"];

let categoryBarChart;
let barChart;
let balanceLineChart;
let currentTransactions = [];
let currentReportPreview = null;
let currentEditTransaction = null;
let categoryHiddenChanges = {};
let lastActivityWrite = 0;
let expiringSession = false;

const typeLabels = {
  INCOME: "Receita",
  EXPENSE: "Despesa"
};

init();

async function init() {
  setupRipple();
  setupInactivityControl();

  const page = currentPage();

  if (page === "index.html" || page === "" || page === "login.html") {
    await initLogin();
    return;
  }

  if (page === "dashboard.html") {
    if (!await requireSession()) {
      return;
    }
    await initDashboard();
    return;
  }

  if (page === "lancamento.html") {
    if (!await requireSession()) {
      return;
    }
    await initTransactionForm();
    return;
  }

  if (page === "editar-lancamento.html") {
    if (!await requireSession()) {
      return;
    }
    await initEditTransactionForm();
    return;
  }

  if (page === "categorias.html") {
    if (!await requireSession()) {
      return;
    }
    await initCategories();
    return;
  }

  if (page === "relatorios.html") {
    if (!await requireSession()) {
      return;
    }
    initReports();
  }
}

function currentPage() {
  return window.location.pathname.split("/").pop();
}

async function initLogin() {
  clearLegacyLocalStorageSession();

  const emailEl = document.getElementById("email");
  const passwordEl = document.getElementById("password");
  const btnLogin = document.getElementById("btnLogin");
  const btnSignup = document.getElementById("btnSignup");
  const btnForgot = document.getElementById("btnForgot");

  btnLogin?.addEventListener("click", async () => {
    clearAuthStatus();

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

      markActivity(true);
      showToast("Login realizado!");
      window.location.href = "dashboard.html";
    } catch (err) {
      setAuthError(`Falha no login: ${err.message}`);
    }
  });

  btnSignup?.addEventListener("click", async () => {
    clearAuthStatus();

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
  });

  btnForgot?.addEventListener("click", async () => {
    clearAuthStatus();

    const email = emailEl.value.trim();

    if (!email) {
      setAuthError("Informe o email para recuperar a senha.");
      return;
    }

    try {
      await forgotPassword(email);
      setAuthSuccess(`Enviamos um link de redefinicao para: ${email}`);
    } catch (err) {
      setAuthError(err.message);
    }
  });
}

async function initDashboard() {
  const now = new Date();
  const firstDay = new Date(now.getFullYear(), now.getMonth(), 1);

  byId("startDate").value = dateToInputValue(firstDay);
  byId("endDate").value = todayISO();

  const session = await getSession();
  byId("userEmail").textContent = session?.user?.email || "";

  byId("btnLogout")?.addEventListener("click", onLogout);
  byId("btnRefresh")?.addEventListener("click", loadTransactions);
  byId("btnApplyFilter")?.addEventListener("click", () => animateRangeChange(loadTransactions));
  byId("transactionsTable")?.addEventListener("click", onTransactionTableClick);

  await loadTransactions();
}

async function initTransactionForm() {
  const dateEl = byId("date");
  const typeEl = byId("type");

  if (dateEl) {
    dateEl.value = todayISO();
  }

  await loadCategoryOptions();

  typeEl?.addEventListener("change", loadCategoryOptions);
  byId("btnAddTransaction")?.addEventListener("click", onAddTransaction);
}

async function initEditTransactionForm() {
  const id = new URLSearchParams(window.location.search).get("id");
  const typeEl = byId("type");

  if (!id) {
    setTxError("Lancamento nao informado.");
    return;
  }

  typeEl?.addEventListener("change", loadCategoryOptions);
  byId("btnUpdateTransaction")?.addEventListener("click", () => updateTransaction(id));
  byId("btnViewCurrentReceipt")?.addEventListener("click", async () => {
    if (currentEditTransaction?.receipt_path) {
      await openReceipt(currentEditTransaction.receipt_path);
    }
  });

  await loadTransactionForEdit(id);
}

async function loadTransactionForEdit(id) {
  const session = await getSession();
  if (!session?.user) {
    window.location.href = "index.html";
    return;
  }

  const { data, error } = await supabase
    .from(TABLE_NAME)
    .select("*")
    .eq("id", id)
    .eq("user_id", session.user.id)
    .single();

  if (error) {
    setTxError(`Erro ao carregar lancamento: ${error.message}`);
    return;
  }

  currentEditTransaction = data;

  byId("type").value = data.type;
  await loadCategoryOptions();

  setCategoryValue(data.category || "");
  byId("amount").value = data.amount;
  byId("date").value = data.date;
  byId("description").value = data.description || "";

  byId("currentReceiptBox")?.classList.toggle("hidden", !data.receipt_path);
}

async function initCategories() {
  byId("btnAddCategory")?.addEventListener("click", addCategory);
  byId("btnSaveCategories")?.addEventListener("click", saveCategoryChanges);
  byId("categoryList")?.addEventListener("click", onCategoryListClick);
  byId("categoryList")?.addEventListener("change", onCategoryListChange);

  await loadCategoryList();
}

function initReports() {
  const now = new Date();
  const firstDay = new Date(now.getFullYear(), now.getMonth(), 1);
  const previousFirstDay = new Date(now.getFullYear(), now.getMonth() - 1, 1);
  const previousLastDay = new Date(now.getFullYear(), now.getMonth(), 0);

  byId("reportStartDate").value = dateToInputValue(firstDay);
  byId("reportEndDate").value = todayISO();
  byId("compareStartDate").value = dateToInputValue(previousFirstDay);
  byId("compareEndDate").value = dateToInputValue(previousLastDay);

  byId("reportType")?.addEventListener("change", onReportTypeChange);
  byId("reportStartDate")?.addEventListener("change", invalidateReportPreview);
  byId("reportEndDate")?.addEventListener("change", invalidateReportPreview);
  byId("compareStartDate")?.addEventListener("change", invalidateReportPreview);
  byId("compareEndDate")?.addEventListener("change", invalidateReportPreview);
  byId("btnPreviewReport")?.addEventListener("click", previewReport);
  byId("btnDownloadReport")?.addEventListener("click", downloadReportPdf);

  onReportTypeChange();
}

async function requireSession() {
  const session = await getSession();

  if (!session?.user || isSessionExpired()) {
    await expireSession();
    window.location.href = "index.html";
    return false;
  }

  markActivity(true);
  return true;
}

async function onLogout() {
  clearPersistedSession();
  showToast("Logout realizado!", "error");
  setTimeout(() => {
    window.location.href = "index.html";
  }, 300);
}

function setupInactivityControl() {
  ACTIVITY_EVENTS.forEach(eventName => {
    window.addEventListener(eventName, async () => {
      const page = currentPage();
      const protectedPage = ["dashboard.html", "lancamento.html", "editar-lancamento.html", "categorias.html", "relatorios.html"].includes(page);

      if (!protectedPage) {
        return;
      }

      if (isSessionExpired()) {
        await expireSession();
        window.location.href = "index.html";
        return;
      }

      markActivity();
    }, { passive: true });
  });

  setInterval(async () => {
    const page = currentPage();
    const protectedPage = ["dashboard.html", "lancamento.html", "editar-lancamento.html", "categorias.html", "relatorios.html"].includes(page);

    if (protectedPage && isSessionExpired()) {
      await expireSession();
      window.location.href = "index.html";
    }
  }, 60 * 1000);
}

function markActivity(force = false) {
  const now = Date.now();

  if (!force && now - lastActivityWrite < 15 * 1000) {
    return;
  }

  lastActivityWrite = now;
  sessionStorage.setItem(LAST_ACTIVITY_KEY, String(now));
}

function isSessionExpired() {
  const lastActivityAt = Number(sessionStorage.getItem(LAST_ACTIVITY_KEY));

  if (!lastActivityAt) {
    return false;
  }

  return Date.now() - lastActivityAt > SESSION_TIMEOUT_MS;
}

async function expireSession() {
  if (expiringSession) {
    return;
  }

  expiringSession = true;

  try {
    clearPersistedSession();
  } finally {
    expiringSession = false;
  }
}

function clearPersistedSession() {
  supabase.auth.signOut().catch(() => {});

  Object.keys(localStorage).forEach(key => {
    if (key.startsWith("sb-") || key === LAST_ACTIVITY_KEY) {
      localStorage.removeItem(key);
    }
  });

  sessionStorage.clear();
}

function clearLegacyLocalStorageSession() {
  Object.keys(localStorage).forEach(key => {
    if (key.startsWith("sb-") || key === LAST_ACTIVITY_KEY) {
      localStorage.removeItem(key);
    }
  });
}

async function onAddTransaction() {
  setTxStatus("");

  const session = await getSession();
  if (!session?.user) {
    window.location.href = "index.html";
    return;
  }

  const amountEl = byId("amount");
  const dateEl = byId("date");
  const typeEl = byId("type");
  const categoryEl = byId("category");
  const descriptionEl = byId("description");
  const receiptFileEl = byId("receiptFile");

  const amount = Number(amountEl.value);
  const date = dateEl.value;

  if (!amount || amount <= 0 || !date) {
    setTxError("Informe valor e data validos.");
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

  const file = receiptFileEl?.files[0];

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
      setTxError(`Salvou, mas erro no comprovante: ${uploadError.message}`);
      return;
    }

    await supabase
      .from(TABLE_NAME)
      .update({ receipt_path: filePath })
      .eq("id", data.id);
  }

  setTxSuccess("Lancamento salvo!");
  showToast("Lancamento salvo!");

  amountEl.value = "";
  descriptionEl.value = "";
  dateEl.value = todayISO();

  if (receiptFileEl) {
    receiptFileEl.value = "";
  }
}

async function updateTransaction(id) {
  setTxStatus("");

  const session = await getSession();
  if (!session?.user) {
    window.location.href = "index.html";
    return;
  }

  const amountEl = byId("amount");
  const dateEl = byId("date");
  const typeEl = byId("type");
  const categoryEl = byId("category");
  const descriptionEl = byId("description");
  const receiptFileEl = byId("receiptFile");

  const amount = Number(amountEl.value);
  const date = dateEl.value;

  if (!amount || amount <= 0 || !date) {
    setTxError("Informe valor e data validos.");
    return;
  }

  const payload = {
    type: typeEl.value,
    amount,
    category: categoryEl.value,
    description: descriptionEl.value.trim() || null,
    date
  };

  const file = receiptFileEl?.files[0];

  if (file) {
    const ext = file.name.split(".").pop();
    const filePath = `${session.user.id}/${id}/comprovante.${ext}`;

    const { error: uploadError } = await supabase.storage
      .from("receipts")
      .upload(filePath, file, {
        cacheControl: "3600",
        upsert: true
      });

    if (uploadError) {
      setTxError(`Erro ao alterar comprovante: ${uploadError.message}`);
      return;
    }

    payload.receipt_path = filePath;

    if (currentEditTransaction?.receipt_path && currentEditTransaction.receipt_path !== filePath) {
      await supabase.storage
        .from("receipts")
        .remove([currentEditTransaction.receipt_path]);
    }
  }

  const { error } = await supabase
    .from(TABLE_NAME)
    .update(payload)
    .eq("id", id)
    .eq("user_id", session.user.id);

  if (error) {
    setTxError(`Erro ao alterar lancamento: ${error.message}`);
    return;
  }

  showToast("Lancamento alterado!");

  setTimeout(() => {
    window.location.href = "dashboard.html";
  }, 1200);
}

async function openReceipt(path) {
  const { data, error } = await supabase.storage
    .from("receipts")
    .createSignedUrl(path, 60);

  if (error) {
    alert(`Erro ao abrir comprovante: ${error.message}`);
    return;
  }

  window.open(data.signedUrl, "_blank");
}

async function loadTransactions() {
  const session = await getSession();
  if (!session?.user) {
    window.location.href = "index.html";
    return;
  }

  const start = byId("startDate")?.value;
  const end = byId("endDate")?.value;

  const { data, error } = await queryTransactions(session.user.id, start, end);

  if (error) {
    setTxError(`Erro ao carregar: ${error.message}`);
    return;
  }

  const previousPeriod = getPreviousPeriod(start, end);
  const { data: previousData, error: previousError } = await queryTransactions(
    session.user.id,
    previousPeriod.start,
    previousPeriod.end
  );

  if (previousError) {
    setTxError(`Erro ao carregar comparativo: ${previousError.message}`);
    return;
  }

  currentTransactions = data || [];
  renderTable(currentTransactions);
  updateDashboard(currentTransactions, previousData || [], { start, end }, previousPeriod);
}

async function queryTransactions(userId, start, end) {
  let query = supabase
    .from(TABLE_NAME)
    .select("*")
    .eq("user_id", userId)
    .order("date", { ascending: false });

  if (start) {
    query = query.gte("date", start);
  }

  if (end) {
    query = query.lte("date", end);
  }

  return await query;
}

function renderTable(list) {
  const transactionsTable = byId("transactionsTable");
  if (!transactionsTable) {
    return;
  }

  transactionsTable.innerHTML = "";

  for (const tx of list) {
    const tr = document.createElement("tr");

    tr.innerHTML = `
      <td>${formatDate(tx.date)}</td>
      <td>${typeLabels[tx.type] || tx.type}</td>
      <td>${tx.category || ""}</td>
      <td>${formatMoney(Number(tx.amount))}</td>
      <td>${tx.description ?? ""}</td>
      <td>${formatDateTimeLocal(tx.created_at)}</td>
      <td>
        <a class="btn btn-light nav-link" href="editar-lancamento.html?id=${tx.id}">Editar</a>
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

async function onTransactionTableClick(e) {
  const btnDelete = e.target.closest(".action-delete");
  if (btnDelete) {
    const id = btnDelete.dataset.id;

    if (!confirm("Deseja excluir este lancamento?")) {
      return;
    }

    await deleteTransaction(id);
    return;
  }

  const btnView = e.target.closest(".action-view");
  if (btnView) {
    await openReceipt(btnView.dataset.path);
  }
}

async function deleteTransaction(id) {
  const { data: tx, error: fetchError } = await supabase
    .from(TABLE_NAME)
    .select("receipt_path")
    .eq("id", id)
    .single();

  if (fetchError) {
    setTxError(`Erro ao buscar comprovante: ${fetchError.message}`);
    return;
  }

  if (tx?.receipt_path) {
    const { error: storageError } = await supabase.storage
      .from("receipts")
      .remove([tx.receipt_path]);

    if (storageError) {
      console.warn("Erro ao apagar comprovante:", storageError.message);
    }
  }

  const { error } = await supabase
    .from(TABLE_NAME)
    .delete()
    .eq("id", id);

  if (error) {
    setTxError(`Erro ao excluir: ${error.message}`);
    return;
  }

  showToast("Lancamento excluido!", "error");
  await loadTransactions();
}

function updateDashboard(list, previousList, period, previousPeriod) {
  const kpiIncome = byId("kpiIncome");
  const kpiExpense = byId("kpiExpense");
  const kpiBalance = byId("kpiBalance");

  if (!kpiIncome || !kpiExpense || !kpiBalance) {
    return;
  }

  const summary = summarizeTransactions(list);
  const previousSummary = summarizeTransactions(previousList);

  kpiIncome.textContent = formatMoney(summary.income);
  kpiExpense.textContent = formatMoney(summary.expense);
  kpiBalance.textContent = formatMoney(summary.balance);

  updateKpiDelta("kpiIncomeDelta", summary.income, previousSummary.income, "periodo anterior");
  updateKpiDelta("kpiExpenseDelta", summary.expense, previousSummary.expense, "periodo anterior", true);
  updateKpiDelta("kpiBalanceDelta", summary.balance, previousSummary.balance, "periodo anterior");

  renderCategoryBarChart(summary.byCategory);
  renderBarChart(summary.income, summary.expense);
  renderBalanceLineChart(list, period);
  renderDashboardInsights(summary, previousSummary, period, previousPeriod);
}

function renderCategoryBarChart(expenseByCategory) {
  const canvas = byId("categoryBarChart");
  if (!canvas || !window.Chart) {
    return;
  }

  const entries = Object.entries(expenseByCategory)
    .filter(([, value]) => value > 0)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 8);

  if (categoryBarChart) {
    categoryBarChart.destroy();
  }

  categoryBarChart = new Chart(canvas, {
    type: "bar",
    data: {
      labels: entries.map(([category]) => category),
      datasets: [{
        label: "Despesas",
        data: entries.map(([, value]) => value),
        backgroundColor: "#2563eb"
      }]
    },
    options: {
      indexAxis: "y",
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          display: false
        },
        tooltip: {
          callbacks: {
            label: ctx => formatMoney(Number(ctx.raw || 0))
          }
        }
      },
      scales: {
        x: {
          ticks: {
            callback: value => formatCompactMoney(Number(value))
          }
        }
      }
    }
  });
}

function renderBarChart(income, expense) {
  const canvas = byId("barChart");
  if (!canvas || !window.Chart) {
    return;
  }

  if (barChart) {
    barChart.destroy();
  }

  barChart = new Chart(canvas, {
    type: "bar",
    data: {
      labels: ["Receita", "Despesa"],
      datasets: [{
        label: "Total no periodo",
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
        },
        tooltip: {
          callbacks: {
            label: ctx => `${ctx.dataset.label}: ${formatMoney(Number(ctx.raw || 0))}`
          }
        }
      },
      scales: {
        y: {
          ticks: {
            callback: value => formatCompactMoney(Number(value))
          }
        }
      }
    }
  });
}

function renderBalanceLineChart(list, period) {
  const canvas = byId("balanceLineChart");
  if (!canvas || !window.Chart) {
    return;
  }

  const points = buildDailyBalancePoints(list, period.start, period.end);

  if (balanceLineChart) {
    balanceLineChart.destroy();
  }

  balanceLineChart = new Chart(canvas, {
    type: "line",
    data: {
      labels: points.map(point => formatDate(point.date)),
      datasets: [
        {
          label: "Receitas acumuladas",
          data: points.map(point => point.income),
          borderColor: "#10b981",
          backgroundColor: "rgba(16, 185, 129, 0.12)",
          tension: 0.25
        },
        {
          label: "Despesas acumuladas",
          data: points.map(point => point.expense),
          borderColor: "#dc2626",
          backgroundColor: "rgba(220, 38, 38, 0.12)",
          tension: 0.25
        },
        {
          label: "Saldo acumulado",
          data: points.map(point => point.balance),
          borderColor: "#2563eb",
          backgroundColor: "rgba(37, 99, 235, 0.12)",
          tension: 0.25
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: {
        intersect: false,
        mode: "index"
      },
      plugins: {
        tooltip: {
          callbacks: {
            label: ctx => `${ctx.dataset.label}: ${formatMoney(Number(ctx.raw || 0))}`
          }
        }
      },
      scales: {
        y: {
          ticks: {
            callback: value => formatCompactMoney(Number(value))
          }
        }
      }
    }
  });
}

function updateKpiDelta(elementId, currentValue, previousValue, label, invertGood = false) {
  const el = byId(elementId);
  if (!el) {
    return;
  }

  const diff = currentValue - previousValue;
  const percent = variationPercent(currentValue, previousValue);
  const isGood = invertGood ? diff <= 0 : diff >= 0;

  el.classList.remove("delta-good", "delta-bad", "delta-neutral");
  el.classList.add(diff === 0 ? "delta-neutral" : isGood ? "delta-good" : "delta-bad");
  el.textContent = `${label}: ${formatMoney(previousValue)} | ${formatMoney(diff)} (${formatPercent(percent)})`;
}

function renderDashboardInsights(summary, previousSummary, period, previousPeriod) {
  const list = byId("dashboardInsights");
  if (!list) {
    return;
  }

  const top = topCategory(summary.byCategory);
  const expenseDiff = summary.expense - previousSummary.expense;
  const balanceDiff = summary.balance - previousSummary.balance;
  const days = getDateRange(period.start, period.end).length || 1;
  const averageExpense = summary.expense / days;
  const periodLabel = `${formatDate(period.start)} a ${formatDate(period.end)}`;
  const previousLabel = `${formatDate(previousPeriod.start)} a ${formatDate(previousPeriod.end)}`;

  const insights = [
    `Periodo analisado: ${periodLabel}. Comparacao automatica com ${previousLabel}.`,
    top
      ? `Maior categoria de despesa: ${top.name}, com ${formatMoney(top.value)} (${formatPercentOfTotal(top.value, summary.expense)} das despesas).`
      : "Nao ha despesas registradas por categoria neste periodo.",
    expenseDiff > 0
      ? `As despesas aumentaram ${formatMoney(expenseDiff)} em relacao ao periodo anterior.`
      : expenseDiff < 0
        ? `As despesas reduziram ${formatMoney(Math.abs(expenseDiff))} em relacao ao periodo anterior.`
        : "As despesas ficaram iguais ao periodo anterior.",
    balanceDiff >= 0
      ? `O saldo melhorou ${formatMoney(Math.abs(balanceDiff))} frente ao periodo anterior.`
      : `O saldo piorou ${formatMoney(Math.abs(balanceDiff))} frente ao periodo anterior.`,
    `Media diaria de despesas no periodo: ${formatMoney(averageExpense)}.`
  ];

  list.innerHTML = insights.map(item => `<li>${item}</li>`).join("");
}

function buildDailyBalancePoints(list, start, end) {
  const dates = getDateRange(start, end);
  const byDate = {};

  list.forEach(tx => {
    if (!byDate[tx.date]) {
      byDate[tx.date] = { income: 0, expense: 0 };
    }

    const value = Number(tx.amount || 0);
    if (tx.type === "INCOME") {
      byDate[tx.date].income += value;
    } else {
      byDate[tx.date].expense += value;
    }
  });

  let income = 0;
  let expense = 0;

  return dates.map(date => {
    income += byDate[date]?.income || 0;
    expense += byDate[date]?.expense || 0;

    return {
      date,
      income,
      expense,
      balance: income - expense
    };
  });
}

function getPreviousPeriod(start, end) {
  if (!start || !end) {
    const now = new Date();
    const firstDay = new Date(now.getFullYear(), now.getMonth(), 1);
    const previousFirstDay = new Date(now.getFullYear(), now.getMonth() - 1, 1);
    const previousLastDay = new Date(now.getFullYear(), now.getMonth(), 0);

    return {
      start: dateToInputValue(previousFirstDay),
      end: dateToInputValue(previousLastDay),
      currentStart: dateToInputValue(firstDay),
      currentEnd: todayISO()
    };
  }

  const startDate = parseInputDate(start);
  const endDate = parseInputDate(end);
  const days = Math.max(1, Math.round((endDate - startDate) / 86400000) + 1);
  const previousEnd = new Date(startDate);
  previousEnd.setDate(previousEnd.getDate() - 1);
  const previousStart = new Date(previousEnd);
  previousStart.setDate(previousStart.getDate() - days + 1);

  return {
    start: dateToInputValue(previousStart),
    end: dateToInputValue(previousEnd)
  };
}

function getDateRange(start, end) {
  if (!start || !end) {
    return [];
  }

  const dates = [];
  const current = parseInputDate(start);
  const last = parseInputDate(end);

  while (current <= last) {
    dates.push(dateToInputValue(current));
    current.setDate(current.getDate() + 1);
  }

  return dates;
}

function parseInputDate(value) {
  const [year, month, day] = value.split("-").map(Number);
  return new Date(year, month - 1, day);
}

function formatCompactMoney(value) {
  return value.toLocaleString("pt-BR", {
    style: "currency",
    currency: "BRL",
    notation: "compact",
    maximumFractionDigits: 1
  });
}

function formatPercentOfTotal(value, total) {
  if (!total) {
    return "0,0%";
  }

  return `${((value / total) * 100).toLocaleString("pt-BR", {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1
  })}%`;
}

function onReportTypeChange() {
  const isComparison = byId("reportType")?.value === "comparison";
  byId("comparisonPeriodFields")?.classList.toggle("hidden", !isComparison);
  invalidateReportPreview();
}

function invalidateReportPreview() {
  byId("btnDownloadReport").disabled = true;
  byId("reportPreviewSection")?.classList.add("hidden");
  currentReportPreview = null;
  setReportStatus("");
}

async function previewReport() {
  setReportStatus("");
  byId("btnDownloadReport").disabled = true;

  const reportType = byId("reportType").value;
  const start = byId("reportStartDate").value;
  const end = byId("reportEndDate").value;
  const compareStart = byId("compareStartDate").value;
  const compareEnd = byId("compareEndDate").value;

  if (!start || !end) {
    setReportError("Informe a data inicial e final do periodo principal.");
    return;
  }

  if (start > end) {
    setReportError("No periodo principal, a data inicial nao pode ser maior que a final.");
    return;
  }

  if (reportType === "comparison" && (!compareStart || !compareEnd)) {
    setReportError("Informe a data inicial e final do periodo de comparacao.");
    return;
  }

  if (reportType === "comparison" && compareStart > compareEnd) {
    setReportError("No periodo de comparacao, a data inicial nao pode ser maior que a final.");
    return;
  }

  const primaryTransactions = await loadTransactionsBetween(start, end);
  const primarySummary = summarizeTransactions(primaryTransactions);

  currentReportPreview = {
    type: reportType,
    primary: {
      start,
      end,
      transactions: primaryTransactions,
      summary: primarySummary
    },
    comparison: null
  };

  if (reportType === "comparison") {
    const comparisonTransactions = await loadTransactionsBetween(compareStart, compareEnd);
    currentReportPreview.comparison = {
      start: compareStart,
      end: compareEnd,
      transactions: comparisonTransactions,
      summary: summarizeTransactions(comparisonTransactions)
    };
  }

  renderReportPreview(currentReportPreview);
  byId("btnDownloadReport").disabled = false;
}

async function loadTransactionsBetween(start, end) {
  const session = await getSession();
  if (!session?.user) {
    window.location.href = "index.html";
    return [];
  }

  const { data, error } = await supabase
    .from(TABLE_NAME)
    .select("*")
    .eq("user_id", session.user.id)
    .gte("date", start)
    .lte("date", end)
    .order("date", { ascending: false });

  if (error) {
    setReportError(`Erro ao carregar relatorio: ${error.message}`);
    return [];
  }

  return data || [];
}

function summarizeTransactions(list) {
  const summary = {
    income: 0,
    expense: 0,
    balance: 0,
    count: list.length,
    byCategory: {}
  };

  for (const tx of list) {
    const value = Number(tx.amount || 0);

    if (tx.type === "INCOME") {
      summary.income += value;
    } else {
      summary.expense += value;
      summary.byCategory[tx.category || "Sem categoria"] =
        (summary.byCategory[tx.category || "Sem categoria"] || 0) + value;
    }
  }

  summary.balance = summary.income - summary.expense;
  return summary;
}

function renderReportPreview(report) {
  const preview = byId("reportPreview");
  const previewSection = byId("reportPreviewSection");

  if (!preview || !previewSection) {
    return;
  }

  const title = reportTitle(report.type);
  const primary = report.primary.summary;

  let html = `
    <div class="preview-summary">
      <h3>${title}</h3>
      <p>Periodo principal: ${formatDate(report.primary.start)} a ${formatDate(report.primary.end)}</p>
      ${summaryCards(primary)}
    </div>
  `;

  if (report.type === "comparison" && report.comparison) {
    const comparison = report.comparison.summary;
    const insights = buildComparisonInsights(primary, comparison);
    html += `
      <div class="preview-summary">
        <h3>Comparacao</h3>
        <p>Periodo comparado: ${formatDate(report.comparison.start)} a ${formatDate(report.comparison.end)}</p>
        ${summaryCards(comparison)}
        ${comparisonInsightsHtml(insights)}
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Indicador</th>
                <th>Periodo principal</th>
                <th>Periodo comparado</th>
                <th>Diferenca</th>
                <th>Variacao</th>
              </tr>
            </thead>
            <tbody>
              ${comparisonRow("Receitas", primary.income, comparison.income)}
              ${comparisonRow("Despesas", primary.expense, comparison.expense)}
              ${comparisonRow("Saldo", primary.balance, comparison.balance)}
            </tbody>
          </table>
        </div>
      </div>
    `;
  }

  if (report.type === "category") {
    html += categoryTable(primary.byCategory);
  }

  if (report.type === "summary") {
    html += transactionTable(report.primary.transactions);
  }

  preview.innerHTML = html;
  previewSection.classList.remove("hidden");
}

function summaryCards(summary) {
  return `
    <div class="kpis report-kpis">
      <div class="card kpi">
        <h3>Receitas</h3>
        <p>${formatMoney(summary.income)}</p>
      </div>
      <div class="card kpi">
        <h3>Despesas</h3>
        <p>${formatMoney(summary.expense)}</p>
      </div>
      <div class="card kpi">
        <h3>Saldo</h3>
        <p>${formatMoney(summary.balance)}</p>
      </div>
    </div>
  `;
}

function comparisonRow(label, primaryValue, comparisonValue) {
  const diff = primaryValue - comparisonValue;
  const percent = variationPercent(primaryValue, comparisonValue);
  return `
    <tr>
      <td>${label}</td>
      <td>${formatMoney(primaryValue)}</td>
      <td>${formatMoney(comparisonValue)}</td>
      <td>${formatMoney(diff)}</td>
      <td>${formatPercent(percent)}</td>
    </tr>
  `;
}

function comparisonInsightsHtml(insights) {
  return `
    <div class="report-insights">
      <h3>Analise do periodo</h3>
      <p>${insights.overview}</p>
      <ul>
        <li>${insights.income}</li>
        <li>${insights.expense}</li>
        <li>${insights.balance}</li>
        <li>${insights.category}</li>
      </ul>
    </div>
  `;
}

function buildComparisonInsights(primary, comparison) {
  const incomeDiff = primary.income - comparison.income;
  const expenseDiff = primary.expense - comparison.expense;
  const balanceDiff = primary.balance - comparison.balance;
  const topPrimaryCategory = topCategory(primary.byCategory);
  const topComparisonCategory = topCategory(comparison.byCategory);

  return {
    overview: balanceDiff >= 0
      ? `O periodo principal teve um saldo ${formatMoney(Math.abs(balanceDiff))} melhor que o periodo comparado.`
      : `O periodo principal teve um saldo ${formatMoney(Math.abs(balanceDiff))} pior que o periodo comparado.`,
    income: describeVariation("Receitas", incomeDiff, primary.income, comparison.income),
    expense: expenseDiff <= 0
      ? `Despesas reduziram ${formatMoney(Math.abs(expenseDiff))} (${formatPercent(variationPercent(primary.expense, comparison.expense))}), indicando melhora no controle de gastos.`
      : `Despesas aumentaram ${formatMoney(expenseDiff)} (${formatPercent(variationPercent(primary.expense, comparison.expense))}), exigindo atencao aos gastos do periodo.`,
    balance: describeVariation("Saldo", balanceDiff, primary.balance, comparison.balance),
    category: topPrimaryCategory
      ? `Maior despesa do periodo principal: ${topPrimaryCategory.name}, com ${formatMoney(topPrimaryCategory.value)}. ${
          topComparisonCategory
            ? `No periodo comparado, a maior despesa foi ${topComparisonCategory.name}, com ${formatMoney(topComparisonCategory.value)}.`
            : "No periodo comparado nao houve despesas por categoria."
        }`
      : "Nao houve despesas por categoria no periodo principal."
  };
}

function describeVariation(label, diff, primaryValue, comparisonValue) {
  const percent = formatPercent(variationPercent(primaryValue, comparisonValue));

  if (diff > 0) {
    return `${label} aumentaram ${formatMoney(diff)} (${percent}) em relacao ao periodo comparado.`;
  }

  if (diff < 0) {
    return `${label} reduziram ${formatMoney(Math.abs(diff))} (${percent}) em relacao ao periodo comparado.`;
  }

  return `${label} permaneceram iguais nos dois periodos.`;
}

function topCategory(byCategory) {
  const entries = Object.entries(byCategory);
  if (!entries.length) {
    return null;
  }

  const [name, value] = entries.sort((a, b) => b[1] - a[1])[0];
  return { name, value };
}

function variationPercent(primaryValue, comparisonValue) {
  if (comparisonValue === 0) {
    return primaryValue === 0 ? 0 : 100;
  }

  return ((primaryValue - comparisonValue) / Math.abs(comparisonValue)) * 100;
}

function formatPercent(value) {
  return `${value >= 0 ? "+" : ""}${value.toLocaleString("pt-BR", {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1
  })}%`;
}

function categoryTable(byCategory) {
  const rows = Object.entries(byCategory)
    .sort((a, b) => b[1] - a[1])
    .map(([category, value]) => `<tr><td>${category}</td><td>${formatMoney(value)}</td></tr>`)
    .join("");

  return `
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Categoria</th>
            <th>Total de despesas</th>
          </tr>
        </thead>
        <tbody>${rows || `<tr><td colspan="2">Nenhuma despesa no periodo.</td></tr>`}</tbody>
      </table>
    </div>
  `;
}

function transactionTable(transactions) {
  const rows = transactions.map(t => `
    <tr>
      <td>${formatDate(t.date)}</td>
      <td>${typeLabels[t.type] || t.type}</td>
      <td>${t.category || ""}</td>
      <td>${formatMoney(Number(t.amount || 0))}</td>
      <td>${t.description || ""}</td>
    </tr>
  `).join("");

  return `
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Data</th>
            <th>Tipo</th>
            <th>Categoria</th>
            <th>Valor</th>
            <th>Descricao</th>
          </tr>
        </thead>
        <tbody>${rows || `<tr><td colspan="5">Nenhum lancamento no periodo.</td></tr>`}</tbody>
      </table>
    </div>
  `;
}

function downloadReportPdf() {
  if (!currentReportPreview) {
    setReportError("Visualize o relatorio antes de baixar o PDF.");
    return;
  }

  const { jsPDF } = window.jspdf;
  const report = currentReportPreview;
  const doc = new jsPDF();
  const title = reportTitle(report.type);

  doc.setFontSize(16);
  doc.text(title, 14, 18);

  doc.setFontSize(11);
  doc.text(`Periodo principal: ${formatDate(report.primary.start)} a ${formatDate(report.primary.end)}`, 14, 28);

  let nextY = addSummaryToPdf(doc, report.primary.summary, 38);

  if (report.type === "comparison" && report.comparison) {
    doc.text(`Periodo comparado: ${formatDate(report.comparison.start)} a ${formatDate(report.comparison.end)}`, 14, nextY);
    nextY = addSummaryToPdf(doc, report.comparison.summary, nextY + 10);
    nextY = addComparisonInsightsToPdf(doc, report.primary.summary, report.comparison.summary, nextY);

    doc.autoTable({
      startY: nextY,
      head: [["Indicador", "Periodo principal", "Periodo comparado", "Diferenca", "Variacao"]],
      body: [
        comparisonPdfRow("Receitas", report.primary.summary.income, report.comparison.summary.income),
        comparisonPdfRow("Despesas", report.primary.summary.expense, report.comparison.summary.expense),
        comparisonPdfRow("Saldo", report.primary.summary.balance, report.comparison.summary.balance)
      ]
    });
  }

  if (report.type === "category") {
    doc.autoTable({
      startY: nextY,
      head: [["Categoria", "Total de despesas"]],
      body: Object.entries(report.primary.summary.byCategory)
        .sort((a, b) => b[1] - a[1])
        .map(([category, value]) => [category, formatMoney(value)])
    });
  }

  if (report.type === "summary") {
    doc.autoTable({
      startY: nextY,
      head: [["Data", "Tipo", "Categoria", "Valor", "Descricao"]],
      body: report.primary.transactions.map(t => [
        formatDate(t.date),
        typeLabels[t.type] || t.type,
        t.category || "",
        formatMoney(Number(t.amount || 0)),
        t.description || ""
      ])
    });
  }

  doc.save(`relatorio-${report.type}-${report.primary.start}-${report.primary.end}.pdf`);
}

function addSummaryToPdf(doc, summary, startY) {
  doc.text(`Receitas: ${formatMoney(summary.income)}`, 14, startY);
  doc.text(`Despesas: ${formatMoney(summary.expense)}`, 14, startY + 8);
  doc.text(`Saldo: ${formatMoney(summary.balance)}`, 14, startY + 16);
  doc.text(`Lancamentos: ${summary.count}`, 14, startY + 24);
  return startY + 34;
}

function comparisonPdfRow(label, primaryValue, comparisonValue) {
  return [
    label,
    formatMoney(primaryValue),
    formatMoney(comparisonValue),
    formatMoney(primaryValue - comparisonValue),
    formatPercent(variationPercent(primaryValue, comparisonValue))
  ];
}

function addComparisonInsightsToPdf(doc, primary, comparison, startY) {
  const insights = buildComparisonInsights(primary, comparison);
  const lines = [
    "Analise do periodo:",
    insights.overview,
    insights.income,
    insights.expense,
    insights.balance,
    insights.category
  ];
  let y = startY;

  lines.forEach(line => {
    const wrapped = doc.splitTextToSize(line, 180);
    doc.text(wrapped, 14, y);
    y += wrapped.length * 6;
  });

  return y + 4;
}

function reportTitle(type) {
  const titles = {
    summary: "Resumo financeiro do periodo",
    comparison: "Comparacao entre periodos",
    category: "Gastos por categoria"
  };

  return titles[type] || "Relatorio";
}

async function addCategory() {
  const nameEl = byId("newCategoryName");
  const typeEl = byId("newCategoryType");
  const name = nameEl.value.trim();
  const type = typeEl.value;

  if (!name) {
    setCategoryError("Informe o nome da categoria.");
    return;
  }

  const session = await getSession();
  if (!session?.user) {
    window.location.href = "index.html";
    return;
  }

  const { data: category, error: categoryError } = await supabase
    .from("categories")
    .insert({
      name,
      type,
      is_default: false
    })
    .select()
    .single();

  if (categoryError) {
    setCategoryError(`Erro ao salvar categoria: ${categoryError.message}`);
    return;
  }

  const { error: linkError } = await supabase.from("user_categories").insert({
    user_id: session.user.id,
    category_id: category.id,
    hidden: false
  });

  if (linkError) {
    await supabase.from("categories").delete().eq("id", category.id);
    setCategoryError(`Erro ao vincular categoria ao usuario: ${linkError.message}`);
    return;
  }

  nameEl.value = "";
  showToast("Categoria salva!");
  await loadCategoryList();
}

async function loadCategoryList() {
  const session = await getSession();
  const categoryList = byId("categoryList");

  if (!session?.user || !categoryList) {
    return;
  }

  const { data: categories, error: categoriesError } = await supabase
    .from("categories")
    .select("*");

  if (categoriesError) {
    setCategoryError(`Erro ao carregar categorias: ${categoriesError.message}`);
    return;
  }

  const { data: userCategories, error: userError } = await supabase
    .from("user_categories")
    .select("*")
    .eq("user_id", session.user.id);

  if (userError) {
    setCategoryError(`Erro ao carregar configuracoes: ${userError.message}`);
    return;
  }

  const userConfigByCategoryId = {};
  (userCategories || []).forEach(userCat => {
    userConfigByCategoryId[userCat.category_id] = userCat;
  });

  categoryList.innerHTML = "";

  (categories || []).forEach(cat => {
    const userConfig = userConfigByCategoryId[cat.id];
    const isOwnCustomCategory = !cat.is_default && Boolean(userConfig);

    if (!cat.is_default && !isOwnCustomCategory) {
      return;
    }

    const hidden = userConfig?.hidden || false;
    const li = document.createElement("li");

    li.className = "category-item";
    li.innerHTML = `
      <span>${cat.name} (${typeLabels[cat.type] || cat.type}) ${cat.is_default ? "*" : ""}</span>
      <div>
        ${
          cat.is_default
            ? `<label class="inline-check">
                <input type="checkbox" data-hide="${cat.id}" ${hidden ? "checked" : ""}>
                Ocultar
              </label>`
            : `
              <button class="btn btn-light" data-user-edit="${cat.id}">Editar</button>
              <button class="btn btn-danger" data-user-delete="${cat.id}">Excluir</button>
            `
        }
      </div>
    `;

    categoryList.appendChild(li);
  });
}

async function onCategoryListClick(e) {
  const session = await getSession();
  if (!session?.user) {
    window.location.href = "index.html";
    return;
  }

  const edit = e.target.dataset.userEdit;
  if (edit) {
    const name = prompt("Novo nome:");
    if (!name) {
      return;
    }

    const { data: ownership } = await supabase
      .from("user_categories")
      .select("id")
      .eq("user_id", session.user.id)
      .eq("category_id", edit)
      .maybeSingle();

    if (!ownership) {
      setCategoryError("Voce so pode editar categorias criadas por voce.");
      return;
    }

    const { error } = await supabase
      .from("categories")
      .update({ name })
      .eq("id", edit)
      .eq("is_default", false);

    if (error) {
      setCategoryError(`Erro ao editar categoria: ${error.message}`);
      return;
    }

    await loadCategoryList();
    return;
  }

  const del = e.target.dataset.userDelete;
  if (del) {
    if (!confirm("Excluir categoria?")) {
      return;
    }

    const { data: ownership } = await supabase
      .from("user_categories")
      .select("id")
      .eq("user_id", session.user.id)
      .eq("category_id", del)
      .maybeSingle();

    if (!ownership) {
      setCategoryError("Voce so pode excluir categorias criadas por voce.");
      return;
    }

    const { error: unlinkError } = await supabase
      .from("user_categories")
      .delete()
      .eq("user_id", session.user.id)
      .eq("category_id", del);

    if (unlinkError) {
      setCategoryError(`Erro ao desvincular categoria: ${unlinkError.message}`);
      return;
    }

    const { error: deleteError } = await supabase
      .from("categories")
      .delete()
      .eq("id", del)
      .eq("is_default", false);

    if (deleteError) {
      setCategoryError(`Erro ao excluir categoria: ${deleteError.message}`);
      return;
    }

    showToast("Categoria excluida!", "error");
    await loadCategoryList();
  }
}

function onCategoryListChange(e) {
  const id = e.target.dataset.hide;
  if (!id) {
    return;
  }

  categoryHiddenChanges[id] = e.target.checked;
}

async function saveCategoryChanges() {
  const session = await getSession();

  if (!session?.user) {
    window.location.href = "index.html";
    return;
  }

  for (const [categoryId, hidden] of Object.entries(categoryHiddenChanges)) {

    await supabase
      .from("user_categories")
      .upsert(
        {
          user_id: session.user.id,
          category_id: categoryId,
          hidden
        },
        {
          onConflict: "user_id,category_id"
        }
      );
  }

  categoryHiddenChanges = {};

  await loadCategoryList();
  showToast("Categorias atualizadas!");
}

async function loadCategoryOptions() {
  const session = await getSession();
  const categoryEl = byId("category");
  const typeEl = byId("type");

  if (!session?.user || !categoryEl || !typeEl) {
    return;
  }

  const { data: categories, error: categoriesError } = await supabase
    .from("categories")
    .select("*");

  if (categoriesError) {
    setTxError(`Erro ao carregar categorias: ${categoriesError.message}`);
    return;
  }

  const { data: userCats, error: userCatsError } = await supabase
    .from("user_categories")
    .select("*")
    .eq("user_id", session.user.id);

  if (userCatsError) {
    setTxError(`Erro ao carregar preferencias: ${userCatsError.message}`);
    return;
  }

  const userMap = {};
  (userCats || []).forEach(u => {
    userMap[u.category_id] = u;
  });

  const selectedType = typeEl.value;
  categoryEl.innerHTML = "";

  (categories || []).forEach(cat => {
    const userConfig = userMap[cat.id];
    const isOwnCustomCategory = !cat.is_default && Boolean(userConfig);

    if (cat.type !== selectedType) {
      return;
    }

    if (!cat.is_default && !isOwnCustomCategory) {
      return;
    }

    if (userConfig?.hidden) {
      return;
    }

    const option = document.createElement("option");
    option.value = cat.name;
    option.textContent = cat.name;
    categoryEl.appendChild(option);
  });
}

function setCategoryValue(categoryName) {
  const categoryEl = byId("category");
  if (!categoryEl || !categoryName) {
    return;
  }

  const hasCategory = Array.from(categoryEl.options)
    .some(option => option.value === categoryName);

  if (!hasCategory) {
    const option = document.createElement("option");
    option.value = categoryName;
    option.textContent = categoryName;
    categoryEl.appendChild(option);
  }

  categoryEl.value = categoryName;
}

function animateRangeChange(callback) {
  const content = document.querySelectorAll(".content-grid, .kpis, .table-wrap");

  content.forEach(el => el.classList.add("range-transition-out"));

  setTimeout(async () => {
    await callback();

    content.forEach(el => {
      el.classList.remove("range-transition-out");
      el.classList.add("range-transition-in");
    });

    setTimeout(() => {
      content.forEach(el => el.classList.remove("range-transition-in"));
    }, 250);
  }, 250);
}

function setupRipple() {
  document.addEventListener("click", e => {
    const btn = e.target.closest(".btn");
    if (!btn) {
      return;
    }

    const circle = document.createElement("span");
    const rect = btn.getBoundingClientRect();
    const size = Math.max(rect.width, rect.height);
    const x = e.clientX - rect.left - size / 2;
    const y = e.clientY - rect.top - size / 2;

    circle.classList.add("ripple");
    circle.style.width = circle.style.height = `${size}px`;
    circle.style.left = `${x}px`;
    circle.style.top = `${y}px`;

    btn.appendChild(circle);

    setTimeout(() => {
      circle.remove();
    }, 600);
  });
}

function formatMoney(value) {
  return value.toLocaleString("pt-BR", {
    style: "currency",
    currency: "BRL"
  });
}

function formatDate(isoDate) {
  if (!isoDate) {
    return "";
  }

  const [year, month, day] = isoDate.split("-");
  return `${day}/${month}/${year}`;
}

function formatDateTimeLocal(isoDateTime) {
  if (!isoDateTime) {
    return "";
  }

  const d = new Date(isoDateTime);
  return d.toLocaleString("pt-BR", {
    timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone
  });
}

function todayISO() {
  return dateToInputValue(new Date());
}

function dateToInputValue(date) {
  const d = new Date();
  d.setTime(date.getTime());

  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
}

function clearAuthStatus() {
  const authStatus = byId("authStatus");
  if (authStatus) {
    authStatus.textContent = "";
  }
}

function setAuthError(msg) {
  const authStatus = byId("authStatus");
  if (authStatus) {
    authStatus.style.color = "#b00020";
    authStatus.textContent = msg;
  }
}

function setAuthSuccess(msg) {
  const authStatus = byId("authStatus");
  if (authStatus) {
    authStatus.style.color = "#0b6b2b";
    authStatus.textContent = msg;
  }
}

function setTxStatus(msg) {
  const txStatus = byId("txStatus");
  if (txStatus) {
    txStatus.textContent = msg;
  }
}

function setTxError(msg) {
  const txStatus = byId("txStatus");
  if (txStatus) {
    txStatus.style.color = "#b00020";
    txStatus.textContent = msg;
  }
}

function setTxSuccess(msg) {
  const txStatus = byId("txStatus");
  if (txStatus) {
    txStatus.style.color = "#0b6b2b";
    txStatus.textContent = msg;
  }
}

function setCategoryError(msg) {
  const categoryStatus = byId("categoryStatus");
  if (categoryStatus) {
    categoryStatus.style.color = "#b00020";
    categoryStatus.textContent = msg;
  }
}

function setReportStatus(msg) {
  const reportStatus = byId("reportStatus");
  if (reportStatus) {
    reportStatus.style.color = "#0b6b2b";
    reportStatus.textContent = msg;
  }
}

function setReportError(msg) {
  const reportStatus = byId("reportStatus");
  if (reportStatus) {
    reportStatus.style.color = "#b00020";
    reportStatus.textContent = msg;
  }
}

function showToast(message, type = "success") {
  const toast = byId("toast");
  if (!toast) {
    return;
  }

  toast.textContent = message;
  toast.style.background = type === "error" ? "#dc2626" : "#10b981";

  toast.classList.remove("hide");
  toast.classList.add("show");

  setTimeout(() => {
    toast.classList.remove("show");
    toast.classList.add("hide");
  }, 2500);
}

function byId(id) {
  return document.getElementById(id);
}

