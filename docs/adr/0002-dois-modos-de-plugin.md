# ADR-0002 — Dois modos de plugin (`java` e `process`)

- **Status:** aceito
- **Data:** 2026-11-13
- **Relacionado:** D2, D16, T3, T4, T4c

## Contexto

A primeira automação real (`receitanet-bx`) é um app jpackage de 32 MB com Derby e AWT. Mas o time
também vai querer plugins pequenos, em Java, usando `Storage` e `Secrets` diretamente. Forçar um
único modelo obrigaria a escolher entre isolamento e ergonomia.

## Decisão

O `plugin.yml` declara `kind`:

- **`java`** — JAR que depende de `sia-api`, carregado em classloader filho no processo do daemon.
- **`process`** — executável próprio, iniciado pelo Core e falado por **JSON Lines** em
  stdin/stdout (`stdio-jsonl-v1`).

O Core adapta o segundo ao mesmo contrato do primeiro (`ProcessAutomationPlugin implements
AutomationPlugin`), então engine, scheduler, workflow, histórico e permissões não sabem a diferença.

## Protocolo `stdio-jsonl-v1`

| Core → plugin | Plugin → Core |
|---|---|
| `describe` | `describe` (metadata + automations) |
| `execute` (runId, automation, params) | `log`, `progress` (streaming) |
| `cancel`, `shutdown` | `result` (status, outputs), `error` |

Uma linha, um objeto JSON. Linha que não é JSON é descartada com log — um `print` esquecido não
derruba o host.

## Consequências

- Plugin em qualquer linguagem, mas com **um run por vez** por processo (v1 não multiplexa).
- Handshake com timeout de 15 s: plugin que não responde é recusado no carregamento.
- `LoadedPlugin` passou a aceitar ausência de classloader.
- O validador de `process` recusa caminho absoluto e `..`; se `command[0]` existir dentro do pacote,
  vira caminho absoluto resolvido pelo Core; senão é tratado como nome no PATH.

## Alternativas descartadas

| Alternativa | Por que não |
|---|---|
| Só in-process | arrastaria fat jars e conflitos de dependência para dentro do daemon |
| Só processo | perde tipagem, `Storage`/`Secrets` diretos e custa start por execução |
| HTTP local para plugins | porta, auth e ciclo de vida extras sem ganho sobre stdio |
| gRPC | geração de código e dependência pesada para um contrato de 4 mensagens |
