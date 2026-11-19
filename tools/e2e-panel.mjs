// E2E mínimo sem dependências: dirige o Edge por CDP e tira print do painel do plugin.
// Uso: node e2e-panel.mjs <url> <caminho-do-png>
const [, , url, outfile] = process.argv;
const CDP = process.env.CDP_URL || "http://127.0.0.1:9333";

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function target() {
  const created = await fetch(`${CDP}/json/new?${encodeURIComponent(url)}`, { method: "PUT" });
  if (created.ok) return created.json();
  const list = await (await fetch(`${CDP}/json/list`)).json();
  const page = list.find((t) => t.type === "page");
  if (!page) throw new Error("sem pagina no Edge");
  return page;
}

function session(wsUrl) {
  const ws = new WebSocket(wsUrl);
  let id = 0;
  const pending = new Map();
  const events = [];
  ws.addEventListener("message", (event) => {
    const msg = JSON.parse(event.data);
    if (msg.id && pending.has(msg.id)) {
      const { resolve, reject } = pending.get(msg.id);
      pending.delete(msg.id);
      msg.error ? reject(new Error(JSON.stringify(msg.error))) : resolve(msg.result);
      return;
    }
    if (msg.method) events.push(msg);
  });
  const ready = new Promise((resolve) => ws.addEventListener("open", resolve));
  return {
    ready,
    events,
    send(method, params = {}, sessionId) {
      const callId = ++id;
      const payload = { id: callId, method, params };
      if (sessionId) payload.sessionId = sessionId;
      ws.send(JSON.stringify(payload));
      return new Promise((resolve, reject) => pending.set(callId, { resolve, reject }));
    },
    close: () => ws.close(),
  };
}

const page = await target();
const cdp = session(page.webSocketDebuggerUrl);
await cdp.ready;

await cdp.send("Page.enable");
await cdp.send("Runtime.enable");
await cdp.send("Emulation.setDeviceMetricsOverride", {
  width: 1440,
  height: 900,
  deviceScaleFactor: 1,
  mobile: false,
});
await cdp.send("Page.navigate", { url });
await sleep(3500);

const clicked = await cdp.send("Runtime.evaluate", {
  expression: `(() => {
    const button = [...document.querySelectorAll("button")].find((b) => b.textContent.includes("Abrir painel"));
    if (!button) return "botao-nao-encontrado";
    button.click();
    return "clicado";
  })()`,
  returnByValue: true,
});
console.log("clique:", clicked.result.value);

await sleep(3000);

const frame = await cdp.send("Runtime.evaluate", {
  expression: `(() => {
    const iframe = document.querySelector("#panel-frame");
    const panel = document.querySelector("#panel");
    return JSON.stringify({
      painelVisivel: panel ? !panel.hidden : false,
      origem: iframe ? iframe.getAttribute("src") : null,
    });
  })()`,
  returnByValue: true,
});
console.log("painel:", frame.result.value);

// clica "Saudar" DENTRO do iframe: o painel sandbox roda como target separado (OOPIF),
// entao anexamos a ele e avaliamos no contexto dele
const { targetInfos } = await cdp.send("Target.getTargets");
const iframeTarget = targetInfos.find(
  (info) => info.type === "iframe" || (info.url || "").includes("/ui/"),
);
if (iframeTarget) {
  const { sessionId } = await cdp.send("Target.attachToTarget", {
    targetId: iframeTarget.targetId,
    flatten: true,
  });
  await cdp.send("Runtime.enable", {}, sessionId);
  await sleep(500);
  await cdp.send(
    "Runtime.evaluate",
    { expression: `document.getElementById("go").click()` },
    sessionId,
  );
  await sleep(4000);
  const result = await cdp.send(
    "Runtime.evaluate",
    {
      expression: `document.getElementById("out").innerText.replace(/\\s+/g, " ").trim()`,
      returnByValue: true,
    },
    sessionId,
  );
  console.log("resultado no painel:", result.result.value);
} else {
  console.log("resultado no painel: target do iframe nao encontrado");
}

const shot = await cdp.send("Page.captureScreenshot", { format: "png" });
const { writeFileSync } = await import("node:fs");
writeFileSync(outfile, Buffer.from(shot.data, "base64"));
console.log("screenshot:", outfile);
cdp.close();
process.exit(0);
