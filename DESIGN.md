---
name: SIA Console
description: Console operacional do Sistema Integrado de Automações — clara, sóbria, feita para quem executa tarefas, não para quem depura HTTP.
colors:
  surface: "#F6F5F1"
  surface-raised: "#FFFFFF"
  surface-sunken: "#ECE9E2"
  ink: "#171A1F"
  ink-soft: "#4C525C"
  ink-faint: "#7B8189"
  line: "#DFDBD2"
  line-strong: "#C9C3B6"
  primary: "#0E5A5E"
  primary-hover: "#0A474B"
  primary-soft: "#E1EDEC"
  success: "#1F6F43"
  success-soft: "#E2EFE6"
  warning: "#8A5A00"
  warning-soft: "#F6EBD5"
  danger: "#9E2B2B"
  danger-soft: "#F6E1E0"
  focus: "#0E5A5E"
typography:
  display:
    fontFamily: "Segoe UI, system-ui, sans-serif"
    fontSize: 30px
    fontWeight: 600
    lineHeight: 1.15
    letterSpacing: -0.02em
  title:
    fontFamily: "Segoe UI, system-ui, sans-serif"
    fontSize: 20px
    fontWeight: 600
    lineHeight: 1.3
  body:
    fontFamily: "Segoe UI, system-ui, sans-serif"
    fontSize: 14px
    fontWeight: 400
    lineHeight: 1.55
  label:
    fontFamily: "Segoe UI, system-ui, sans-serif"
    fontSize: 12px
    fontWeight: 600
    lineHeight: 1.2
    letterSpacing: 0.04em
  mono:
    fontFamily: "Cascadia Mono, Consolas, monospace"
    fontSize: 13px
    fontWeight: 400
    lineHeight: 1.5
rounded:
  sm: 6px
  md: 10px
  lg: 16px
  full: 999px
spacing:
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 40px
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "#FFFFFF"
    rounded: "{rounded.md}"
    padding: 10px
  button-ghost:
    backgroundColor: "{colors.surface-raised}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
    padding: 10px
  card:
    backgroundColor: "{colors.surface-raised}"
    rounded: "{rounded.lg}"
    padding: 20px
  input:
    backgroundColor: "#FFFFFF"
    rounded: "{rounded.sm}"
    padding: 9px
---

# SIA Console

## Overview

Quem usa este console é **operador de automação**, não desenvolvedor: a pessoa sabe qual tarefa
precisa rodar (sincronizar catálogo, baixar guias, conferir pedidos), não sabe o que é `POST`.
A interface existe para transformar um catálogo de automações em **formulários executáveis** e um
histórico em **respostas compreensíveis**.

Tom: calmo, técnico, sem drama. Papel quente em vez de branco clínico; uma cor de ação só; o resto
é tipografia e espaço. Nada de gradiente, glassmorphism ou animação decorativa.

## Colors

- **Primary (#0E5A5E)** — única cor de ação: botão Executar, link ativo, foco. Nunca em decoração.
- **Surface (#F6F5F1)** — fundo papel; **surface-raised (#FFFFFF)** — cartões.
- **Ink (#171A1F)** — texto principal; **ink-soft** — secundário; **ink-faint** — metadados.
- **Line (#DFDBD2)** — divisores e bordas de 1px no lugar de sombra.
- **Success/Warning/Danger** — só em status: pílulas de `SUCCESS`, `RUNNING`, `FAILURE`.

## Typography

Uma família sem serifa em todo o texto; monoespaçada apenas para **ids, datas e log**. Rótulo em
caixa alta com `letter-spacing` para separar seções. Nada de mais de 3 pesos por tela.

## Layout

Barra lateral fixa de 232px com navegação; conteúdo em coluna de até 1080px com `padding` de 32px.
Grades de cartões com `gap` de 16px; em telas estreitas a barra vira topo e as grades viram uma
coluna. Tudo alinhado à esquerda — a leitura é de cima para baixo.

## Elevation & Depth

Uma única camada elevada: **drawer** de detalhe da execução. Fora isso, profundidade é **borda de
1px** e diferença de tom entre surface e surface-raised. Sombra só no drawer.

## Shapes

Cantos de 10px em controles e 16px em cartões. Pílulas de status totalmente arredondadas. Nada de
canto vivo convivendo com arredondado na mesma tela.

## Components

- **Botão primário**: fundo `primary`, texto branco, altura 38px, largura pelo conteúdo. Um por tela.
- **Botão secundário**: fundo branco, borda `line`, texto `ink`.
- **Campo**: rótulo acima, altura 38px, borda `line`, foco com anel de 2px `primary-soft`.
- **Pílula de status**: fundo suave da cor + texto forte, 11px, caixa alta.
- **Cartão de automação**: nome, descrição, contagem de parâmetros e botão Executar.
- **Drawer**: 480px à direita, cabeçalho com status + id curto, corpo com resultado, parâmetros e log.

## Painel de plugin

Todo plugin traz a própria tela — é obrigatório no manifesto. O catálogo mostra **um cartão por plugin**, nunca um por automação: as operações vivem dentro do painel. O console é só a moldura: cartão
branco de 16px, cabeçalho com nome e versão do plugin, `iframe` ocupando o resto. O conteúdo dentro é
do plugin; não aplicamos o nosso sistema de design ali, e é isso que permite telas ricas (prévia,
tabela, etapas).

O painel abre centralizado, com fundo escurecido a 36%, largura e altura vindas do manifesto (padrão
560×460), no máximo `100vw - 48px`. Fechar é no ✕, no clique fora ou no `Esc` — sempre.

## Do's and Don'ts

- Do mostrar **consequência** antes de executar: o que a automação faz e quais campos são obrigatórios.
- Do traduzir erro de máquina (`BOT_BANCO_OCUPADO`) em frase com próxima ação.
- Don't usar `primary` para duas ações concorrentes na mesma tela.
- Don't exibir token, parâmetro sensível ou saída de valor — o Core já não devolve, a UI não deve pedir.
- Don't depender de ícone sozinho: todo ícone tem rótulo ou `aria-label`.
