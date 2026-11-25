# ADR-0006 — Interface visual própria do plugin

- **Status:** aceito — **revisado em 2026-11-19: `ui` passou a ser obrigatória**
- **Data:** 2026-11-19
- **Relacionado:** D2, D8, T15, T17, T18

## Revisão (2026-11-19)

A primeira versão deixava `ui` opcional e mantinha o formulário gerado como padrão. Na prática, a
tela gerada não dava conta do uso real: o usuário precisa escolher operação, ver o que vai acontecer
e acompanhar o resultado. A decisão foi tornar a interface **obrigatória**:

- o validador recusa manifesto sem `ui.entry`;
- o console removeu o formulário gerado (`openRunForm`) e sempre abre o painel do plugin;
- o `sia:init` passou a entregar **todas as automações do plugin**, para o painel montar a própria
  navegação (o painel de exemplo usa isso para o seletor de operação);
- o archetype e o plugin de exemplo (`hello-plugin`) ganharam painel.

## Contexto

A primeira versão do console gerava um formulário a partir do `ParamSpec` de cada automação. Serve
para “preencher e executar”, mas não para o uso real: escolher arquivo, ver prévia, acompanhar etapas,
decidir no meio do processo. O pedido do usuário foi explícito — **cada plugin deveria ter a própria
tela, como um plugin de áudio**.

## Decisão

O `plugin.yml` pode declarar uma interface:

```yaml
ui:
  entry: ui/panel.html
  width: 560
  height: 460
```

O plugin embarca o HTML/CSS/JS no próprio pacote. O daemon serve esses arquivos em
`GET /api/plugins/{id}/ui/{caminho}`, restritos à pasta declarada. O console abre a página num
`iframe` com `sandbox="allow-scripts allow-forms"` e conversa por `postMessage`.

Sem `ui`, o comportamento antigo continua: formulário gerado dos parâmetros.

### Mensagens

| Direção | Tipos |
|---|---|
| console → painel | `sia:init`, `sia:started`, `sia:result` |
| painel → console | `sia:ready`, `sia:execute`, `sia:close` |

### Valores de saída

O histórico continua guardando **apenas chaves** (D8). Para o painel exibir resultado, a execução
pedida pela tela usa `POST /api/runs?wait=true`, que devolve as saídas em memória sem persistir.

## Consequências

- Plugin passa a ter UI versionada junto com o código, sem build de frontend no Core.
- O painel é isolado: sem cookie, sem `localStorage` do console, sem acesso ao topo.
- Caminho de arquivo é validado duas vezes (manifesto e requisição) e a leitura é limitada a 5 MB.
- Permissões não mudam: a execução pedida pelo painel passa pelo Core e pelas mesmas regras.
- Plugin `kind: process` também pode ter painel, desde que embarcado no pacote servido.
- Formulário gerado permanece como padrão e como fallback para automação sem tela.

## Alternativas descartadas

| Alternativa | Por que não |
|---|---|
| JSON de layout renderizado pelo console | limita a “telinha” a campos e tabelas simples |
| Plugin rodando servidor próprio em outra porta | porta, auth, ciclo de vida e CORS extras |
| Web Components injetados no documento principal | sem isolamento: JS do plugin poderia tocar a sessão |
| Build React por plugin | obrigaria Node no fluxo de quem escreve plugin |

## Riscos

- **Painel quebrado não derruba o console**: iframe isolado; o pior caso é uma moldura vazia.
- **Fonte de confiança**: um painel é código de terceiro rodando no navegador do operador. O sandbox
  remove sessão e navegação, mas ainda vê as mensagens que o console envia — o que é o mínimo para
  funcionar.
