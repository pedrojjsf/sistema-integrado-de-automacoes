# ADR-0001 — `receitanet-bx` como plugin `kind: java`

- **Status:** aceito
- **Data:** 2026-11-13
- **Decisores:** time SIA
- **Relacionado:** D14 (primeiro plugin), D15 (`ProcessRunner`), D16 (protocolo `stdio-jsonl-v1`)

## Contexto

O SIA aceita dois modos de plugin: `java` (jar que depende de `sia-api`, roda no daemon) e
`process` (executável próprio, falado por JSON Lines). A primeira automação real é o
**ReceitanetBX-Bot**: aplicação Java 23 empacotada com jpackage, com CLI headless completa
(`lote-cnpj`, `pedidos`, `baixar`, `diag`, `conectar`), banco Derby local e certificado ICP-Brasil
do repositório do Windows.

Desde a migração do SIA para Java 23, o bytecode do bot já é compatível — então "não carrega" deixou
de ser argumento. A escolha voltou a ser arquitetural.

## Decisão

O plugin `receitanet-bx` será **`kind: java`**, um adaptador fino que:

1. monta os argumentos do bot a partir dos parâmetros da automação;
2. valida entradas (formato de data, ids de sistema) antes de gerar processo;
3. invoca o bot por `ProcessRunner` (allowlist `processes.receitanet-bx` → comando base);
4. interpreta a saída e devolve `AutomationResult` com `errorCode` estável.

O bot continua sendo **processo separado**, iniciado pelo Core.

## Justificativa

- **Reuso dos serviços do Core:** allowlist de executável, timeout, limite de saída, redação de
  parâmetros sensíveis e kill de árvore já existem no `ProcessRunner`; no modo `process` o adaptador
  teria de reimplementar isso e falar JSON Lines.
- **Contrato tipado:** `AutomationDescriptor`, `AutomationResult` e `ParamSpec` são usados direto,
  sem serialização intermediária de descritores.
- **Um artefato:** `plugin.jar` + `plugin.yml`; no modo `process` seria pasta com script + runtime
  + manifesto.
- **Isolamento preservado onde importa:** o risco real é o motor oficial do Receitanet (Derby,
  AWT, assinatura), e ele já roda fora da JVM do daemon.

## Consequências

- O plugin depende de `sia-api`; subir a major da API exige republicar o plugin (política SemVer já
  prevista).
- O plugin precisa da permissão `process` e de uma entrada na allowlist do `config.yml`.
- Se um dia o bot for reescrito em outra linguagem, o caminho é `kind: process` — o Core já suporta,
  e a troca não afeta catálogo, permissões nem histórico.

## Alternativas descartadas

| Alternativa | Por que não |
|---|---|
| `kind: process` (wrapper em script) | reimplementaria timeout, allowlist e redação; mais um runtime para versionar |
| Carregar o bot in-process | fat jar de 32 MB com Derby e AWT; lock exclusivo do Derby com o programa oficial; crash do motor derrubaria o daemon |
| Recompilar o bot para outra versão | desnecessário após a migração do SIA para Java 23 |
