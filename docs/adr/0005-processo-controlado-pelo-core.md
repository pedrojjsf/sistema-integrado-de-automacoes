# ADR-0005 — Processo executado pelo Core, com allowlist

- **Status:** aceito
- **Data:** 2026-11-13
- **Relacionado:** D15, T4b

## Contexto

O plugin do Receitanet precisa executar um binário externo. Se o plugin recebesse `ProcessBuilder`,
a permissão `process` viraria execução arbitrária — qualquer plugin poderia rodar `cmd`, `curl` ou
qualquer coisa com o usuário do daemon.

## Decisão

O Core expõe `ProcessRunner` e o plugin informa apenas um **nome lógico**:

```yaml
processes:
  receitanet-bx:
    command: ["${java.home}/bin/java", "-cp", "app/receitanetbx-bot.jar", "numberia.bot.Inicio"]
    timeout-minutes: 15
```

```java
ProcessResult r = ctx.processes().run(
    ProcessRequest.of("receitanet-bx", "diag").withTimeout(Duration.ofMinutes(2)));
```

O `DefaultProcessRunner` aplica:

- permissão `process` antes de qualquer spawn;
- allowlist (nome inexistente → `ProcessException.NOT_ALLOWED` antes de subir processo);
- drenagem paralela de stdout/stderr (evita deadlock de pipe);
- corte em 256 KB com drenagem contínua;
- `redactValues` para mascarar o que aparecer na saída;
- kill da árvore de processos no timeout.

## Consequências

- Plugin não escolhe caminho; administrador escolhe o que existe.
- `${java.home}` e `${env:VAR}` são resolvidos na subida, então a config não tem caminho absoluto de
  máquina.
- Para plugins `kind: process` o Core inicia o processo a partir do manifesto, com caminho validado
  (relativo ao pacote, sem `..`).

## Alternativas descartadas

| Alternativa | Por que não |
|---|---|
| `ProcessBuilder` no plugin | execução arbitrária autorizada pelo manifesto |
| Shell via `cmd /c` | injeção por argumento e dependência de shell |
| Container por plugin | infra e peso desproporcionais nesta fase |
