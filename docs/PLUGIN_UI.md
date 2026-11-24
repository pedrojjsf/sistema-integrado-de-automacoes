# Interface própria do plugin

Até a versão anterior, o console gerava um formulário a partir dos parâmetros da automação. Isso
serve para automação simples, mas não para fluxo de verdade: escolher arquivo, ver prévia, acompanhar
etapas, decidir no meio.

Agora **o plugin pode trazer a própria tela** — como a janela de um plugin de áudio. O SIA abre a
moldura, entrega os dados e recebe o pedido de execução; o desenho é do plugin.

```
┌─────────────── console do SIA ───────────────┐
│  Automações › Hello                          │
│  ┌────────────────────────────────────────┐  │
│  │  iframe (sandbox) — ui/panel.html      │  │
│  │  Saudação                              │  │
│  │  [ Camila        ]  [ Saudar ]         │  │
│  │  Ola, Camila                           │  │
│  └────────────────────────────────────────┘  │
└──────────────────────────────────────────────┘
```

## Obrigatória desde a v1

Todo plugin declara `ui`. Não existe mais o formulário gerado pelo console: se o plugin não trouxer
a página, o manifesto é recusado no carregamento com

```
ui: obrigatorio: declare ui.entry com a pagina do plugin (ex.: ui/panel.html)
```

| Situação | Interface |
|---|---|
| 1–3 campos, sem decisão | painel próprio simples (como o `hello-plugin`) |
| escolher arquivo, prévia, tabela, etapas | painel próprio completo |
| automação disparada só por agenda | painel mínimo, só para execução manual |

## Declarar no manifesto

```yaml
id: meu-plugin
kind: java
ui:
  entry: ui/panel.html   # caminho relativo dentro do pacote
  width: 560             # opcional (320–2000)
  height: 460            # opcional (240–2000)
```

Regras validadas no carregamento:

- `entry` é **relativo**, com `/`, terminando em `.html`;
- nada de `..`, caminho absoluto, `:` (drive), query ou fragmento;
- o arquivo tem de existir dentro do pacote (JAR do plugin ou pasta do plugin `kind: process`);
- o plugin só serve arquivos da pasta declarada (`ui/` no exemplo). Pedir `plugin.yml` ou
  `../qualquer` é recusado com 400 antes de tocar no disco.

## Como o SIA serve esses arquivos

```
GET /api/plugins/{id}/ui/{caminho}
```

Resposta com o tipo certo (`text/html`, `text/css`, `text/javascript`, `image/png`, …), `Cache-Control:
no-cache` e limite de 5 MB por arquivo. Funciona para plugin carregado de JAR e de pasta.

## O protocolo do painel (postMessage)

O painel roda em um `iframe` com `sandbox="allow-scripts allow-forms"` — sem acesso ao `localStorage`
do console, sem `top.location`, sem cookie. A conversa é só por mensagens.

### Console → painel

| Mensagem | Quando | Conteúdo |
|---|---|---|
| `sia:init` | ao abrir | `plugin: {id, name, version}`, `automation` (a que foi clicada, ou `null`), `automations` (todas as do plugin) |
| `sia:started` | execução aceita | `automation` |
| `sia:result` | execução terminou | `status`, `errorCode`, `message`, `outputs` **com valores**, `durationMillis`, `runId` |

### Painel → console

| Mensagem | Efeito |
|---|---|
| `sia:ready` | painel carregado (o console pode enviar `sia:init` de novo) |
| `sia:execute` | `{automation, params}` — o console executa e responde com `sia:result` |
| `sia:close` | fecha o painel |

### Exemplo mínimo

```html
<button id="go">Executar</button>
<script>
  document.getElementById("go").onclick = () =>
    parent.postMessage({ type: "sia:execute", automation: "meu-plugin.rodar", params: { modo: "rapido" } }, "*");

  addEventListener("message", (event) => {
    const msg = event.data || {};
    if (msg.type === "sia:result") {
      console.log(msg.status, msg.outputs);   // outputs vem com valores
    }
  });
</script>
```

## Por que os valores vêm para o painel

O histórico do SIA guarda apenas as **chaves** de saída — nunca os valores. Mas uma tela precisa
mostrar o resultado. A solução: a execução pedida pelo painel usa `POST /api/runs?wait=true`, que
devolve as saídas **em memória** para quem chamou, sem gravá-las:

```
POST /api/runs?wait=true
{"runId":"9ac2…","status":"SUCCESS","outputs":{"saudacao":"Ola, Camila"},"durationMillis":594}

GET /api/runs?limit=1      → outputs: ["saudacao"]   (só a chave, como sempre)
```

Isso mantém a decisão D8 (nada sensível em banco) e ainda permite tela rica.

## O que o painel **não** pode

- ler ou gravar dados fora do que recebe nas mensagens;
- acessar `localStorage`/cookies do console (sandbox);
- abrir janela, navegar o topo ou rodar plugin de terceiro;
- executar automação sem permissão: o pedido passa pelo Core, que aplica as mesmas regras.

## Checklist de um painel

- [ ] `ui.entry` no manifesto e arquivo dentro do pacote
- [ ] funciona em 520×460 (tamanho padrão) e em tela estreita
- [ ] trata `sia:result` com `status`, `errorCode` e `outputs` vazios
- [ ] desabilita o botão enquanto executa (evita duplo clique)
- [ ] não depende de recurso externo (CDN, fonte remota)
- [ ] testado com `hello-plugin` como referência

## Referência

[examples/hello-plugin](https://github.com/pedrojjsf/sistema-integrado-de-automacoes/tree/main/examples/hello-plugin)
tem um painel completo (formulário, botão, resultado e mensagens de erro).
