/* SIA Console — SPA sem build, falando com a API REST do daemon. */
(() => {
  "use strict";

  const TOKEN_KEY = "sia.token";
  const TERMINAL = new Set(["SUCCESS", "FAILURE", "SKIPPED", "CANCELLED"]);
  const state = { token: localStorage.getItem(TOKEN_KEY) || "", view: "painel", health: null, timer: null };
  let authMode = null;

  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

  /* ---------------- infra ---------------- */

  function h(tag, attrs = {}, ...children) {
    const node = document.createElement(tag);
    for (const [key, value] of Object.entries(attrs || {})) {
      if (value === null || value === undefined || value === false) continue;
      if (key === "class") node.className = value;
      else if (key === "html") node.innerHTML = value;
      else if (key.startsWith("on")) node.addEventListener(key.slice(2), value);
      else if (key === "dataset") Object.assign(node.dataset, value);
      else node.setAttribute(key, value === true ? "" : value);
    }
    for (const child of children.flat()) {
      if (child === null || child === undefined || child === false) continue;
      node.append(child.nodeType ? child : document.createTextNode(String(child)));
    }
    return node;
  }

  async function api(path, options = {}) {
    const headers = { ...(options.headers || {}) };
    if (state.token) headers.Authorization = `Bearer ${state.token}`;
    if (options.body !== undefined) headers["Content-Type"] = "application/json";
    const response = await fetch(path, {
      ...options,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });
    if (response.status === 401 || response.status === 403) {
      // auth.mode=none nao tem login: 403 ali e permissao de papel, nao sessao
      if (response.status === 401 && authMode !== "none") {
        state.token = "";
        localStorage.removeItem(TOKEN_KEY);
        showLogin("Sessão expirada. Entre novamente.");
      }
      throw new Error("sem permissão para esta ação");
    }
    const text = await response.text();
    const data = text ? JSON.parse(text) : null;
    if (!response.ok) throw new Error((data && (data.error || data.message)) || `falha HTTP ${response.status}`);
    return data;
  }

  function toast(message, kind = "info") {
    const node = $("#toast");
    node.textContent = message;
    node.className = `toast${kind === "error" ? " error" : ""}`;
    node.hidden = false;
    clearTimeout(node._timer);
    node._timer = setTimeout(() => (node.hidden = true), 4200);
  }

  const dateTime = (iso) => (iso ? new Date(iso).toLocaleString("pt-BR") : "—");
  const shortId = (id) => (id ? `${id.slice(0, 8)}…` : "—");
  const duration = (run) => {
    if (!run.startedAt) return "—";
    const end = run.finishedAt ? new Date(run.finishedAt) : new Date();
    const ms = Math.max(0, end - new Date(run.startedAt));
    return ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1)} s`;
  };

  function statusPill(status) {
    const map = {
      SUCCESS: "pill-success",
      RUNNING: "pill-warning",
      QUEUED: "pill-neutral",
      FAILURE: "pill-danger",
      FAILED: "pill-danger",
      TIMEOUT: "pill-danger",
      CANCELLED: "pill-neutral",
      SKIPPED: "pill-neutral",
      ENABLED: "pill-success",
      DISABLED: "pill-neutral",
    };
    return h("span", { class: `pill ${map[status] || "pill-neutral"}` }, status || "—");
  }

  const EXPLAIN = {
    PERMISSION_DENIED: "o plugin tentou usar um recurso que não está autorizado no manifesto",
    BOT_BANCO_OCUPADO: "o programa do Receitanet está aberto; feche-o e execute de novo",
    BOT_TIMEOUT: "o robô passou do tempo limite; verifique a conexão e tente novamente",
    TIMEOUT: "a execução passou do tempo limite",
    PLUGIN_TIMEOUT: "o plugin de processo não respondeu a tempo",
    PLUGIN_ERROR: "o plugin relatou um erro",
  };

  function explainRun(run) {
    if (!run.errorCode) return run.message || "";
    return `${EXPLAIN[run.errorCode] || run.errorCode}${run.message ? ` — ${run.message}` : ""}`;
  }

  /* ---------------- login ---------------- */

  function showLogin(message) {
    // hidden + display inline: independe de qualquer regra de CSS carregada
    $("#app").hidden = true;
    $("#app").style.display = "none";
    $("#login").hidden = false;
    $("#login").style.display = "grid";
    if (message) {
      const error = $("#login-error");
      error.textContent = message;
      error.hidden = false;
    }
  }

  function startApp() {
    $("#login").hidden = true;
    $("#login").style.display = "none";
    $("#app").hidden = false;
    $("#app").style.display = "grid";
    if (authMode === "none") {
      const logout = $("#logout");
      if (logout) logout.hidden = true;
      const label = $("#conn-label");
      if (label) label.textContent = "acesso local";
    }
    navigate(location.hash.replace("#/", "") || "painel");
  }

  $("#login-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const token = $("#token-input").value.trim();
    if (!token) return;
    state.token = token;
    try {
      await api("/api/health");
      localStorage.setItem(TOKEN_KEY, token);
      $("#login-error").hidden = true;
      startApp();
    } catch (error) {
      state.token = "";
      showLogin("Não foi possível entrar: " + error.message);
    }
  });

  $("#logout").addEventListener("click", () => {
    localStorage.removeItem(TOKEN_KEY);
    state.token = "";
    showLogin();
  });

  /* ---------------- navegação ---------------- */

  const VIEWS = {
    painel: { label: "Painel", title: "Visão geral", render: renderPainel },
    automacoes: { label: "Automações", title: "O que você pode executar", render: renderAutomacoes },
    historico: { label: "Histórico", title: "Execuções", render: renderHistorico },
    agendas: { label: "Agendas", title: "Execuções automáticas", render: renderAgendas },
    workflows: { label: "Fluxos", title: "Fluxos de trabalho", render: renderWorkflows },
    plugins: { label: "Plugins", title: "Plugins instalados", render: renderPlugins },
  };

  function navigate(view) {
    if (!VIEWS[view]) view = "painel";
    state.view = view;
    location.hash = `#/${view}`;
    const config = VIEWS[view];
    $("#view-eyebrow").textContent = config.label;
    $("#view-title").textContent = config.title;
    $$(".nav a").forEach((link) => link.classList.toggle("active", link.dataset.view === view));
    loadConnection();
    config.render();
  }

  window.addEventListener("hashchange", () => {
    if (!$("#app").hidden) navigate(location.hash.replace("#/", ""));
  });

  $("#refresh").addEventListener("click", () => navigate(state.view));

  async function loadConnection() {
    const dot = $("#conn-dot");
    const label = $("#conn-label");
    try {
      state.health = await api("/api/health");
      dot.className = "status-dot on";
      label.textContent = `v${state.health.version} · ${state.health.auth}`;
    } catch {
      dot.className = "status-dot off";
      label.textContent = "sem conexão";
    }
  }

  /* ---------------- painel ---------------- */

  async function renderPainel() {
    const content = $("#content");
    content.replaceChildren(h("div", { class: "empty" }, "Carregando…"));
    try {
      const [health, runs, plugins] = await Promise.all([
        api("/api/health"),
        api("/api/runs?limit=6"),
        api("/api/plugins"),
      ]);

      const enabled = plugins.filter((p) => p.state === "ENABLED").length;
      const failures = runs.filter((r) => r.status !== "SUCCESS" && r.status !== "RUNNING");

      content.replaceChildren(
        h(
          "div",
          { class: "card-grid" },
          statCard("Execuções em andamento", health.running, "agora"),
          statCard("Plugins ativos", `${enabled}/${plugins.length}`, "habilitados"),
          statCard("Falhas recentes", failures.length, "últimas 6 execuções"),
          statCard("Automações disponíveis", plugins.reduce((sum, p) => sum + p.automations.length, 0), "no catálogo"),
        ),
        h(
          "section",
          { class: "card" },
          h(
            "div",
            { class: "card-head" },
            h("div", {}, h("h3", { class: "title" }, "Últimas execuções"),
              h("p", { class: "faint" }, "Clique para ver o resultado e o log")),
            h("button", { class: "btn btn-sm", type: "button", onclick: () => navigate("automacoes") }, "Executar automação"),
          ),
          runs.length === 0
            ? h("p", { class: "muted", style: "margin-top:12px" }, "Nenhuma execução ainda. Comece pela aba Automações.")
            : runsTable(runs),
        ),
        health.failures && health.failures.length
          ? h(
              "section",
              { class: "card" },
              h("h3", { class: "title" }, "Plugins que não carregaram"),
              h("ul", { class: "muted", style: "margin:10px 0 0;padding-left:18px" },
                health.failures.map((failure) => h("li", {}, failure))),
            )
          : null,
      );
    } catch (error) {
      content.replaceChildren(h("div", { class: "empty" }, "Não foi possível carregar: " + error.message));
    }
  }

  function statCard(label, value, hint) {
    return h(
      "div",
      { class: "card" },
      h("div", { class: "stat" },
        h("span", { class: "stat-label" }, label),
        h("span", { class: "stat-value" }, String(value)),
        h("span", { class: "faint" }, hint)),
    );
  }

  function runsTable(runs) {
    return h(
      "table",
      { class: "table", style: "margin-top:12px" },
      h("thead", {}, h("tr", {},
        h("th", {}, "Automação"), h("th", {}, "Status"), h("th", {}, "Quando"),
        h("th", {}, "Duração"), h("th", {}, "Origem"), h("th", {}, ""))),
      h("tbody", {}, runs.map((run) =>
        h("tr", {},
          h("td", {}, h("div", { class: "mono" }, run.automation),
            h("div", { class: "faint" }, shortId(run.id))),
          h("td", {}, statusPill(run.status)),
          h("td", { class: "muted" }, dateTime(run.startedAt)),
          h("td", { class: "muted" }, duration(run)),
          h("td", {}, h("span", { class: "faint" }, run.actor || run.trigger)),
          h("td", { class: "num" },
            h("button", { class: "link-btn", type: "button", onclick: () => openRun(run.id) }, "Detalhes"))))),
    );
  }

  /* ---------------- automações ---------------- */

  async function renderAutomacoes() {
    const content = $("#content");
    content.replaceChildren(h("div", { class: "empty" }, "Carregando catálogo…"));
    try {
      const [automations, plugins] = await Promise.all([api("/api/automations"), api("/api/plugins")]);
      // a automacao pode nao ter o prefixo do id do plugin (hello.say -> hello-plugin):
      // o vinculo correto vem da lista de automacoes publicada por cada plugin
      const byAutomation = new Map();
      for (const plugin of plugins) {
        for (const automationId of plugin.automations) byAutomation.set(automationId, plugin);
      }
      if (automations.length === 0) {
        content.replaceChildren(h("div", { class: "empty" }, "Nenhuma automação instalada ainda."));
        return;
      }
      content.replaceChildren(
        h("p", { class: "muted" }, "Escolha o que executar. Automações com interface própria abrem o painel do plugin."),
        h("div", { class: "card-grid" }, automations.map((automation) =>
          automationCard(automation, byAutomation.get(automation.id)))),
      );
    } catch (error) {
      content.replaceChildren(h("div", { class: "empty" }, "Falha ao carregar: " + error.message));
    }
  }

  function automationCard(automation, plugin) {
    const disabled = !plugin || plugin.state !== "ENABLED";
    const hasPanel = Boolean(plugin && plugin.ui);
    const paramCount = automation.params.length;
    return h(
      "article",
      { class: "card" },
      h("div", { class: "card-head" },
        h("div", {},
          h("h3", { class: "title" }, automation.name || automation.id),
          h("p", { class: "mono faint", style: "margin-top:2px" }, automation.id)),
        hasPanel
          ? h("span", { class: "pill pill-success" }, "interface própria")
          : paramCount
              ? h("span", { class: "pill pill-neutral" }, `${paramCount} campo${paramCount > 1 ? "s" : ""}`)
              : null),
      h("p", { class: "muted", style: "margin-top:10px;min-height:40px" }, automation.description || "Sem descrição."),
      h("div", { class: "row", style: "margin-top:14px;justify-content:space-between" },
        disabled ? h("span", { class: "pill pill-neutral" }, "plugin desabilitado") : h("span"),
        h("button", {
          class: "btn btn-primary btn-sm",
          type: "button",
          disabled,
          onclick: () => (hasPanel ? openPluginPanel(plugin, automation) : openRunForm(automation)),
        }, hasPanel ? "Abrir painel" : "Executar")),
    );
  }

  /* ---------------- painel do plugin ---------------- */

  function openPluginPanel(plugin, automation) {
    const frame = $("#panel-frame");
    $("#panel-eyebrow").textContent = `${plugin.name} · v${plugin.version}`;
    $("#panel-title").textContent = automation ? automation.name || automation.id : plugin.name;
    const window_ = $(".panel-window");
    window_.style.setProperty("--panel-w", `${plugin.ui.width}px`);
    window_.style.setProperty("--panel-h", `${plugin.ui.height}px`);
    $("#panel").hidden = false;
    document.body.style.overflow = "hidden";
    state.panel = { plugin, frame, automation };
    frame.onload = () => sendToPanel({ type: "sia:init", plugin: { id: plugin.id, name: plugin.name }, automation });
    frame.src = `/api/plugins/${encodeURIComponent(plugin.id)}/ui/${plugin.ui.entry}`;
  }

  function sendToPanel(message) {
    const frame = state.panel && state.panel.frame;
    if (frame && frame.contentWindow) frame.contentWindow.postMessage(message, "*");
  }

  function closePanel() {
    const frame = $("#panel-frame");
    $("#panel").hidden = true;
    document.body.style.overflow = "";
    state.panel = null;
    frame.onload = null;
    frame.src = "about:blank";
  }

  async function runFromPanel(automationId, params) {
    const automation = automationId || (state.panel && state.panel.automation && state.panel.automation.id);
    if (!automation) {
      sendToPanel({ type: "sia:result", status: "FAILURE", errorCode: "AUTOMATION_UNDEFINED", message: "automação não informada" });
      return;
    }
    try {
      sendToPanel({ type: "sia:started", automation });
      // wait=true devolve as saídas em memória para o painel exibir (o histórico guarda só chaves)
      const result = await api("/api/runs?wait=true", {
        method: "POST",
        body: { automation, params: params || {} },
      });
      sendToPanel({
        type: "sia:result",
        automation,
        runId: result.runId,
        status: result.status,
        errorCode: result.errorCode,
        message: result.message || EXPLAIN[result.errorCode] || "",
        outputs: result.outputs || {},
        durationMillis: result.durationMillis,
      });
      loadConnection();
    } catch (error) {
      sendToPanel({ type: "sia:result", automation, status: "FAILURE", message: error.message });
    }
  }

  async function waitRun(runId) {
    for (let attempt = 0; attempt < 600; attempt++) {
      const run = await api(`/api/runs/${runId}`);
      if (TERMINAL.has(run.status)) return run;
      await new Promise((resolve) => setTimeout(resolve, 700));
    }
    throw new Error("a execução não terminou no tempo esperado");
  }

  window.addEventListener("message", (event) => {
    const panel = state.panel;
    if (!panel || !panel.frame || event.source !== panel.frame.contentWindow) return;
    const message = event.data || {};
    switch (message.type) {
      case "sia:execute":
        runFromPanel(message.automation, message.params);
        break;
      case "sia:log":
        break; // o log oficial é o do Core
      case "sia:close":
        closePanel();
        break;
      default:
        break;
    }
  });

  function openRunForm(automation) {
    const fields = automation.params.map((param) => {
      const id = `param-${param.name}`;
      let input;
      if (param.type === "BOOLEAN") {
        input = h("input", { id, name: param.name, type: "checkbox", style: "width:auto;height:auto" });
      } else {
        input = h("input", {
          id,
          name: param.name,
          type: param.sensitive ? "password" : param.type === "INTEGER" ? "number" : "text",
          placeholder: param.sensitive ? "não é exibido depois" : "",
          required: param.required,
          autocomplete: "off",
        });
      }
      const label = param.type === "BOOLEAN"
        ? h("label", { class: "row", for: id, style: "gap:8px" }, input, h("span", {}, param.name))
        : h("label", { class: "field", for: id },
            h("span", { class: "field-label" }, param.name + (param.required ? " *" : "")),
            input,
            param.sensitive ? h("span", { class: "field-hint" }, "valor sensível: não aparece no histórico") : null);
      return { param, label, input };
    });

    const body = h("div", { class: "stack" },
      h("p", { class: "muted" }, automation.description || "Preencha os campos e execute."),
      fields.length
        ? h("div", { class: "form-grid" }, fields.map((field) => field.label))
        : h("p", { class: "faint" }, "Esta automação não precisa de nenhum campo."));

    const foot = h("div", { class: "row", style: "justify-content:flex-end;width:100%" },
      h("button", { class: "btn", type: "button", "data-close": "drawer" }, "Cancelar"),
      h("button", { class: "btn btn-primary", type: "button", onclick: submit }, "Executar agora"));

    openDrawer({
      eyebrow: "Executar",
      title: automation.name || automation.id,
      body,
      foot,
    });

    async function submit() {
      const params = {};
      for (const { param, input } of fields) {
        if (param.type === "BOOLEAN") {
          if (input.checked) params[param.name] = true;
          continue;
        }
        const value = input.value.trim();
        if (!value) {
          if (param.required) {
            toast(`Preencha o campo ${param.name}.`, "error");
            input.focus();
            return;
          }
          continue;
        }
        params[param.name] = param.type === "INTEGER" ? Number(value) : value;
      }
      foot.querySelectorAll("button").forEach((button) => (button.disabled = true));
      try {
        const created = await api("/api/runs", { method: "POST", body: { automation: automation.id, params } });
        watchRun(created.runId, automation.name || automation.id);
      } catch (error) {
        toast(error.message, "error");
        foot.querySelectorAll("button").forEach((button) => (button.disabled = false));
      }
    }
  }

  function watchRun(runId, title) {
    const body = h("div", { class: "stack" },
      h("p", { class: "mono faint" }, `execução ${runId}`),
      h("div", { class: "progress" }, h("span")),
      h("p", { class: "muted" }, "Executando… pode levar alguns minutos."));
    openDrawer({ eyebrow: "Em execução", title, body, foot: null });

    clearInterval(state.timer);
    state.timer = setInterval(async () => {
      try {
        const run = await api(`/api/runs/${runId}`);
        if (!TERMINAL.has(run.status)) return;
        clearInterval(state.timer);
        openRun(runId);
        toast(run.status === "SUCCESS" ? "Execução concluída com sucesso." : "A execução terminou com falha.", run.status === "SUCCESS" ? "info" : "error");
      } catch (error) {
        clearInterval(state.timer);
        toast(error.message, "error");
      }
    }, 900);
  }

  /* ---------------- histórico ---------------- */

  async function renderHistorico() {
    const content = $("#content");
    content.replaceChildren(h("div", { class: "empty" }, "Carregando execuções…"));
    try {
      const runs = await api("/api/runs?limit=40");
      if (!runs.length) {
        content.replaceChildren(h("div", { class: "empty" }, "Nenhuma execução registrada."));
        return;
      }
      content.replaceChildren(
        h("div", { class: "card" },
          h("div", { class: "card-head" },
            h("div", {}, h("h3", { class: "title" }, "Últimas 40 execuções"),
              h("p", { class: "faint" }, "Quem executou, quando e com qual resultado")),
            h("button", { class: "btn btn-sm", type: "button", onclick: () => navigate("historico") }, "Atualizar")),
          runsTable(runs)),
      );
    } catch (error) {
      content.replaceChildren(h("div", { class: "empty" }, "Falha ao carregar: " + error.message));
    }
  }

  async function openRun(runId) {
    openDrawer({ eyebrow: "Execução", title: "Carregando…", body: h("div", { class: "empty" }, "…"), foot: null });
    try {
      const [run, logs] = await Promise.all([api(`/api/runs/${runId}`), api(`/api/runs/${runId}/logs`)]);
      const body = h("div", { class: "stack" },
        h("div", { class: "row" }, statusPill(run.status), h("span", { class: "faint" }, dateTime(run.startedAt)),
          h("span", { class: "faint" }, duration(run))),
        run.message || run.errorCode ? h("div", { class: "card", style: "background:var(--surface)" },
          h("p", { class: "field-label" }, "Resultado"),
          h("p", {}, explainRun(run))) : null,
        h("dl", { class: "kv" },
          dt("Automação"), dd(run.automation),
          dt("Plugin"), dd(run.plugin),
          dt("Executado por"), dd(run.actor),
          dt("Origem"), dd(run.trigger),
          dt("Parâmetros"), dd(run.params && run.params.length ? run.params.join(", ") : "nenhum"),
          dt("Saídas"), dd(run.outputs && run.outputs.length ? run.outputs.join(", ") : "nenhuma")),
        h("div", {}, h("p", { class: "field-label", style: "margin-bottom:8px" }, "Log"),
          h("div", { class: "log" }, (logs || []).join("\n"))));
      const foot = h("div", { class: "row", style: "justify-content:flex-end;width:100%" },
        run.status === "RUNNING"
          ? h("button", { class: "btn btn-danger", type: "button", onclick: () => cancelRun(runId) }, "Cancelar execução")
          : null,
        h("button", { class: "btn", type: "button", "data-close": "drawer" }, "Fechar"));
      openDrawer({ eyebrow: "Execução", title: run.automation, body, foot });
    } catch (error) {
      toast(error.message, "error");
      closeDrawer();
    }
  }

  const dt = (label) => h("dt", {}, label);
  const dd = (value) => h("dd", {}, value || "—");

  async function cancelRun(runId) {
    try {
      await api(`/api/runs/${runId}`, { method: "DELETE" });
      toast("Cancelamento solicitado.");
      closeDrawer();
      navigate(state.view);
    } catch (error) {
      toast(error.message, "error");
    }
  }

  /* ---------------- agendas ---------------- */

  function describeTrigger(trigger) {
    if (!trigger) return "—";
    if (trigger.expression) return `cron ${trigger.expression}`;
    if (trigger.every) return `a cada ${trigger.every}`;
    if (trigger.type && typeof trigger.type === "string") return trigger.type.toLowerCase();
    return JSON.stringify(trigger);
  }

  async function renderAgendas() {
    const content = $("#content");
    content.replaceChildren(h("div", { class: "empty" }, "Carregando agendas…"));
    try {
      const [schedules, automations] = await Promise.all([api("/api/schedules"), api("/api/automations")]);
      const form = h("form", { class: "card stack", onsubmit: createSchedule },
        h("div", {},
          h("h3", { class: "title" }, "Nova agenda"),
          h("p", { class: "faint" }, "Escolha a automação e com que frequência ela deve rodar.")),
        h("div", { class: "form-grid" },
          h("label", { class: "field" }, h("span", { class: "field-label" }, "Automação"),
            h("select", { name: "automation", required: true },
              h("option", { value: "" }, "selecione…"),
              automations.map((a) => h("option", { value: a.id }, a.name || a.id)))),
          h("label", { class: "field" }, h("span", { class: "field-label" }, "Frequência"),
            h("select", { name: "frequency", onchange: syncFrequency },
              h("option", { value: "daily" }, "todo dia às 03:00"),
              h("option", { value: "hourly" }, "a cada hora"),
              h("option", { value: "interval" }, "a cada X minutos"))),
          h("label", { class: "field", id: "cron-field", hidden: true },
            h("span", { class: "field-label" }, "Expressão cron (opcional)"),
            h("input", { name: "cron", type: "text", placeholder: "0 0 3 * * ?" })),
          h("label", { class: "field", id: "minutes-field", hidden: true },
            h("span", { class: "field-label" }, "Intervalo em minutos"),
            h("input", { name: "minutes", type: "number", min: "1", value: "15" }))),
        h("div", { class: "row", style: "justify-content:flex-end" },
          h("button", { class: "btn btn-primary", type: "submit" }, "Criar agenda")));

      content.replaceChildren(
        form,
        h("div", { class: "card" },
          h("h3", { class: "title" }, "Agendas ativas"),
          schedules.length === 0
            ? h("p", { class: "muted", style: "margin-top:10px" }, "Nenhuma agenda criada ainda.")
            : h("table", { class: "table", style: "margin-top:12px" },
                h("thead", {}, h("tr", {}, h("th", {}, "Automação"), h("th", {}, "Quando"),
                  h("th", {}, "Próxima"), h("th", {}, "Estado"), h("th", {}, ""))),
                h("tbody", {}, schedules.map((schedule) =>
                  h("tr", {},
                    h("td", { class: "mono" }, schedule.automation),
                    h("td", { class: "muted" }, describeTrigger(schedule.trigger)),
                    h("td", { class: "muted" }, dateTime(schedule.nextFireAt)),
                    h("td", {}, statusPill(schedule.enabled ? "ENABLED" : "DISABLED")),
                    h("td", { class: "num" },
                      h("button", { class: "link-btn", type: "button", onclick: () => toggleSchedule(schedule) },
                        schedule.enabled ? "Pausar" : "Retomar"),
                      h("button", { class: "link-btn", type: "button", style: "margin-left:12px;color:var(--danger)", onclick: () => deleteSchedule(schedule) }, "Remover"))))))),
      );
    } catch (error) {
      content.replaceChildren(h("div", { class: "empty" }, "Falha ao carregar: " + error.message));
    }
  }

  function syncFrequency(event) {
    const value = event.target.value;
    $("#cron-field").hidden = value !== "daily";
    $("#minutes-field").hidden = value !== "interval";
  }

  async function createSchedule(event) {
    event.preventDefault();
    const form = event.target;
    const data = new FormData(form);
    const automation = data.get("automation");
    if (!automation) return toast("Escolha uma automação.", "error");
    const frequency = data.get("frequency");
    let trigger;
    if (frequency === "interval") {
      const minutes = Number(data.get("minutes") || 15);
      trigger = { type: "interval", every: minutes * 60 };
    } else if (frequency === "hourly") {
      trigger = { type: "cron", expression: "0 0 * * * ?" };
    } else {
      trigger = { type: "cron", expression: (data.get("cron") || "").trim() || "0 0 3 * * ?" };
    }
    try {
      await api("/api/schedules", { method: "POST", body: { automation, trigger, params: {} } });
      toast("Agenda criada.");
      renderAgendas();
    } catch (error) {
      toast(error.message, "error");
    }
  }

  async function toggleSchedule(schedule) {
    try {
      await api(`/api/schedules/${schedule.id}/${schedule.enabled ? "disable" : "enable"}`, { method: "POST" });
      renderAgendas();
    } catch (error) {
      toast(error.message, "error");
    }
  }

  async function deleteSchedule(schedule) {
    if (!confirm(`Remover a agenda de ${schedule.automation}?`)) return;
    try {
      await api(`/api/schedules/${schedule.id}`, { method: "DELETE" });
      toast("Agenda removida.");
      renderAgendas();
    } catch (error) {
      toast(error.message, "error");
    }
  }

  /* ---------------- fluxos ---------------- */

  async function renderWorkflows() {
    const content = $("#content");
    content.replaceChildren(h("div", { class: "empty" }, "Carregando fluxos…"));
    try {
      const [names, runs] = await Promise.all([api("/api/workflows"), api("/api/workflows/runs?limit=10")]);
      content.replaceChildren(
        h("div", { class: "card" },
          h("h3", { class: "title" }, "Fluxos disponíveis"),
          h("p", { class: "faint" }, "Um fluxo encadeia várias automações; a ordem é definida no arquivo do fluxo."),
          names.length === 0
            ? h("p", { class: "muted", style: "margin-top:10px" }, "Nenhum fluxo publicado.")
            : h("div", { class: "card-grid", style: "margin-top:14px" },
                names.map((name) => h("article", { class: "card" },
                  h("h4", { class: "title" }, name),
                  h("p", { class: "mono faint" }, `workflows/${name}.yml`),
                  h("div", { class: "row", style: "justify-content:flex-end;margin-top:12px" },
                    h("button", { class: "btn btn-primary btn-sm", type: "button", onclick: () => runWorkflow(name) }, "Executar fluxo")))))),
        h("div", { class: "card" },
          h("h3", { class: "title" }, "Execuções de fluxo"),
          runs.length === 0
            ? h("p", { class: "muted", style: "margin-top:10px" }, "Nenhuma execução de fluxo ainda.")
            : h("table", { class: "table", style: "margin-top:12px" },
                h("thead", {}, h("tr", {}, h("th", {}, "Fluxo"), h("th", {}, "Status"), h("th", {}, "Etapas"))),
                h("tbody", {}, runs.map((run) =>
                  h("tr", {},
                    h("td", { class: "mono" }, run.name),
                    h("td", {}, statusPill(run.status)),
                    h("td", { class: "muted" },
                      Object.entries(run.nodes || {}).map(([id, status]) => `${id}: ${status}`).join(" · "))))))),
      );
    } catch (error) {
      content.replaceChildren(h("div", { class: "empty" }, "Falha ao carregar: " + error.message));
    }
  }

  async function runWorkflow(name) {
    try {
      const run = await api(`/api/workflows/${encodeURIComponent(name)}/runs`, { method: "POST", body: { inputs: {} } });
      toast(`Fluxo ${name} iniciado.`);
      openDrawer({
        eyebrow: "Fluxo",
        title: name,
        body: h("div", { class: "stack" },
          h("p", { class: "mono faint" }, `execução ${run.id}`),
          h("p", {}, `Status: ${run.status}`),
          h("dl", { class: "kv" }, Object.entries(run.nodes || {}).flatMap(([id, status]) => [h("dt", {}, id), h("dd", {}, status)]))),
        foot: h("button", { class: "btn", type: "button", "data-close": "drawer" }, "Fechar"),
      });
      renderWorkflows();
    } catch (error) {
      toast(error.message, "error");
    }
  }

  /* ---------------- plugins ---------------- */

  async function renderPlugins() {
    const content = $("#content");
    content.replaceChildren(h("div", { class: "empty" }, "Carregando plugins…"));
    try {
      const plugins = await api("/api/plugins");
      content.replaceChildren(
        h("p", { class: "muted" }, "Desabilitar um plugin impede novas execuções até ele ser habilitado de novo."),
        plugins.length === 0
          ? h("div", { class: "empty" }, "Nenhum plugin instalado.")
          : h("div", { class: "card-grid" }, plugins.map((plugin) =>
              h("article", { class: "card" },
                h("div", { class: "card-head" },
                  h("div", {},
                    h("h3", { class: "title" }, plugin.name),
                    h("p", { class: "mono faint" }, `${plugin.id} · v${plugin.version}`)),
                  statusPill(plugin.state)),
                h("p", { class: "muted", style: "margin-top:10px" }, `${plugin.automations.length} automação(ões)`),
                h("div", { class: "row", style: "justify-content:flex-end;margin-top:14px" },
                  h("button", {
                    class: "btn btn-sm",
                    type: "button",
                    onclick: () => pluginAction(plugin, plugin.state === "ENABLED" ? "disable" : "enable"),
                  }, plugin.state === "ENABLED" ? "Desabilitar" : "Habilitar"),
                  h("button", {
                    class: "btn btn-sm btn-danger",
                    type: "button",
                    onclick: () => removePlugin(plugin),
                  }, "Remover"))))),
      );
    } catch (error) {
      content.replaceChildren(h("div", { class: "empty" }, "Falha ao carregar: " + error.message));
    }
  }

  async function pluginAction(plugin, action) {
    try {
      await api(`/api/plugins/${plugin.id}/${action}`, { method: "POST" });
      toast(action === "enable" ? "Plugin habilitado." : "Plugin desabilitado.");
      renderPlugins();
      loadConnection();
    } catch (error) {
      toast(error.message, "error");
    }
  }

  async function removePlugin(plugin) {
    if (!confirm(`Remover ${plugin.name}? O plugin deixa de estar disponível até ser instalado de novo.`)) return;
    try {
      await api(`/api/plugins/${plugin.id}/uninstall`, { method: "POST" });
      toast("Plugin removido.");
      renderPlugins();
      loadConnection();
    } catch (error) {
      toast(error.message, "error");
    }
  }

  /* ---------------- drawer ---------------- */

  function openDrawer({ eyebrow, title, body, foot }) {
    $("#drawer-eyebrow").textContent = eyebrow || "";
    $("#drawer-title").textContent = title || "";
    $("#drawer-body").replaceChildren(body);
    const footer = $("#drawer-foot");
    footer.replaceChildren();
    if (foot) footer.append(foot);
    $("#drawer").hidden = false;
    document.body.style.overflow = "hidden";
  }

  function closeDrawer() {
    $("#drawer").hidden = true;
    document.body.style.overflow = "";
    clearInterval(state.timer);
  }

  document.addEventListener("click", (event) => {
    if (event.target.closest("[data-close='drawer']")) closeDrawer();
    if (event.target.closest("[data-close='panel']")) closePanel();
  });

  document.addEventListener("keydown", (event) => {
    if (event.key !== "Escape") return;
    if (!$("#panel").hidden) closePanel();
    else if (!$("#drawer").hidden) closeDrawer();
  });

  /* ---------------- boot ---------------- */

  async function boot() {
    try {
      const health = await api("/api/health");
      state.health = health;
      authMode = health.auth;
      // sem login configurado: entra direto
      if (authMode === "none") {
        startApp();
        return;
      }
      if (state.token) {
        startApp();
        return;
      }
    } catch (error) {
      // 401 esperado quando auth=dev e ainda nao ha chave
    }
    showLogin();
  }

  boot();
})();
