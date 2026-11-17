# ADR-0004 — Nada sensível em banco ou log

- **Status:** aceito
- **Data:** 2026-11-13
- **Relacionado:** D8, T5, T6

## Contexto

Automações baixam arquivos fiscais, e-mails e documentos que contêm dado pessoal. Log e histórico são
passivos: uma vez gravados, ninguém sabe o que vazou nem por quanto tempo ficou guardado.

## Decisão

O Core registra **quem, quando e o que** — nunca o conteúdo:

1. `ParamSpec.sensitive` marca o parâmetro; o Core deriva daí os valores a mascarar.
2. `POST /api/runs` grava parâmetros **apenas como nomes**.
3. `run_log` recebe mensagem por template; `RunLog` aplica `Redactor` por valor **antes** de gravar
   e de imprimir, inclusive no que vem de exceção e de saída de processo.
4. `outputs` persistem só as **chaves**.

## Prova

`NoSensitiveDataTest` roda um plugin deliberadamente descuidado — que concatena o CPF na mensagem e
devolve o protocolo como saída — e verifica:

```
run.params_keys  = [cpf]
run.outputs_keys = [protocolo]
run_log          = "processando CPF ***"
bytes de .db + -wal  →  sem CPF, sem protocolo
```

## Consequências

- Redação é do Core, não uma convenção que o autor pode esquecer.
- `persistOutputs` é opt-in por automação quando a saída precisar ser auditável.
- Workflow persiste saídas por nó para permitir retomada — automação que devolver dado sensível como
  saída deve marcá-lo como `sensitive` (o valor é redigido no log, e a saída é decisão do autor).
