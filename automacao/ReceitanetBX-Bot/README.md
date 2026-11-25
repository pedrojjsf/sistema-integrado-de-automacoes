# Receitanet BX Bot (binário oficial, não é código do SIA)

Esta pasta contém o **bot oficial do Receitanet BX** — um app `jpackage` de terceiros (32 MB, com
banco Derby e AWT) que o plugin [`integrations/receitanet-bx`](../../integrations/receitanet-bx)
aciona via `ProcessRunner`.

- `app/` e `runtime/` — artefatos extraídos do executável oficial (fora do git).
- `ReceitanetBX-Bot.exe` — o binário oficial (fora do git).
- `banco-proprio.bat` — atalho que abre o programa oficial para configurar banco/certificado.
- `LEIA-ME.txt` — instruções originais do fornecedor.

## Por que está aqui e não em `integrations/`

É um **binário de terceiros**, não algo que o SIA compila. O daemon referencia o `app/receitanetbx-bot.jar`
por configuração (`config/local.yml`, seção `processes.receitanet-bx`), não por módulo Maven. A pasta
fica fora da árvore de módulos de propósito: o build do SIA nunca toca nela.

## Atualizar o bot

1. Fechar o programa oficial.
2. Substituir `app/` e `runtime/` pelos artefatos da nova versão.
3. Rodar `receitanet.diag` pelo console para confirmar `sistemas: 21`.
