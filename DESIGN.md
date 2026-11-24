---
name: SIA Console
description: Console operacional do Sistema Integrado de Automações — sóbrio, denso na medida, feito para quem executa tarefas.
colors:
  paper: "#F6F5F1"
  card: "#FFFFFF"
  sunken: "#EEECE6"
  sidebar: "#101519"
  sidebar-raised: "#171D22"
  ink: "#14171B"
  ink-soft: "#545B65"
  ink-faint: "#858B94"
  on-dark: "#E8ECEF"
  on-dark-soft: "#9AA3AC"
  line: "#E4E0D7"
  line-strong: "#CFC9BA"
  primary: "#0E5A5E"
  primary-hover: "#0A474B"
  primary-soft: "#E3EFEE"
  success: "#1F6F43"
  warning: "#8A5A00"
  danger: "#9E2B2B"
typography:
  display:
    fontFamily: "Segoe UI, system-ui, sans-serif"
    fontSize: 28px
    fontWeight: 650
    lineHeight: 1.15
  page-title:
    fontFamily: "Segoe UI, system-ui, sans-serif"
    fontSize: 24px
    fontWeight: 650
    lineHeight: 1.2
  title:
    fontFamily: "Segoe UI, system-ui, sans-serif"
    fontSize: 17px
    fontWeight: 600
    lineHeight: 1.3
  body:
    fontFamily: "Segoe UI, system-ui, sans-serif"
    fontSize: 14px
    fontWeight: 400
    lineHeight: 1.55
  small:
    fontFamily: "Segoe UI, system-ui, sans-serif"
    fontSize: 12.5px
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "Segoe UI, system-ui, sans-serif"
    fontSize: 11px
    fontWeight: 650
    lineHeight: 1.2
    letterSpacing: 0.07em
  mono:
    fontFamily: "Cascadia Mono, Consolas, monospace"
    fontSize: 12.5px
    fontWeight: 400
rounded:
  sm: 6px
  md: 9px
  lg: 14px
  full: 999px
spacing:
  xs: 4px
  sm: 8px
  md: 14px
  lg: 20px
  xl: 32px
components:
  sidebar:
    backgroundColor: "{colors.sidebar}"
    padding: 20px
  card:
    backgroundColor: "{colors.card}"
    rounded: "{rounded.lg}"
    padding: 20px
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "#FFFFFF"
    rounded: "{rounded.md}"
    height: 34px
---

# SIA Console

## Overview

Quem usa este console é **operador de automação**, não desenvolvedor. A interface existe para
transformar um catálogo de plugins em telas executáveis e um histórico em respostas compreensíveis.

Tom: calmo, técnico, sem drama. Papel quente, uma única cor de ação (verde-petróleo) e uma barra
lateral escura que dá contraste e hierarquia. Nada de gradiente decorativo, glassmorphism ou
animação que não comunique estado.

## Colors

- **Sidebar (#101519)** — âncora de contraste; itens em cinza-claro, ativo com barra de 2px em
  `#3D9D97` e fundo `#16333A`. É o que separa "estrutura" de "conteúdo".
- **Primary (#0E5A5E)** — só para ação: botão primário, link, foco. Nunca em decoração.
- **Paper (#F6F5F1)** e **Card (#FFFFFF)** — superfícies; **Sunken (#EEECE6)** para pílulas neutras.
- **Line (#E4E0D7)** — divisores de 1px no lugar de sombra.
- **Success / Warning / Danger** — apenas estado: `SUCCESS`, `RUNNING`/`FAILURE`, erro de formulário.

## Typography

Uma família sem serifa em todo o texto; monoespaçada só para **ids, caminhos, datas e log**. Rótulos
em caixa alta com `letter-spacing` de 0.07em. Três pesos por tela, no máximo: 400, 600 e 650.

## Layout

Barra lateral fixa de 248px. Conteúdo em coluna de até 1200px, `padding` de 32px, `gap` de 18px
entre seções. Grades de cartões com mínimo de 320px. Em telas estreitas a barra vira topo e as
grades viram uma coluna.

## Elevation & Depth

Cartões têm sombra de 1px e borda; no hover sobem 1px com sombra de 26px. Diálogo e drawer usam a
sombra forte (80px) com fundo escurecido a 36%. Nenhum outro elemento tem sombra.

## Shapes

Cantos de 9px em controles, 14px em cartões e pílulas totalmente arredondadas. A mesma linguagem em
toda a tela — nunca canto vivo convivendo com arredondado.

## Components

- **Faixa de indicadores**: um cartão com quatro células separadas por linha; rótulo 11px, valor 26px.
- **Cartão de plugin**: nome + versão em mono, descrição, operações em uma linha, rodapé separado
  por linha com último status e o convite "Abrir painel →". O cartão inteiro é clicável.
- **Pílula de status**: fundo suave da cor + texto forte, 10.5px, caixa alta, `nowrap`.
- **Tabela**: cabeçalho 10.5px caixa alta, linhas de 1px, hover em `#FBFAF7`, ações à direita.
- **Lista**: linhas com divisor de 1px dentro do cartão (usada em Fluxos), sem cartão dentro de cartão.
- **Painel de plugin**: moldura do console com cabeçalho de 1 linha; o conteúdo é do plugin.

## Do's and Don'ts

- Do mostrar **um cartão por plugin**: as operações vivem dentro do painel, não espalhadas.
- Do usar a sidebar escura para separar estrutura de conteúdo; não repita a cor no conteúdo.
- Do preferir uma linha de texto a um bloco quando a informação é secundária.
- Don't usar `primary` para duas ações concorrentes na mesma tela.
- Don't exibir valor de parâmetro sensível — o Core já não devolve, a UI não deve pedir.
- Don't empilhar cartão dentro de cartão: use lista com divisores.
