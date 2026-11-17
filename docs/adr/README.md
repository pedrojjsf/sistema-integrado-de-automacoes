# Decisões de arquitetura (ADRs)

| # | Decisão | Resumo | Registro |
|---|---|---|---|
| D1 | Plataforma | Java 23 + Maven multi-módulo | abaixo |
| D2 | Dois modos de plugin | `java` in-process e `process` por JSON Lines | [ADR-0002](0002-dois-modos-de-plugin.md) |
| D3 | Permissões | capability-based na fachada, deny-by-default | abaixo |
| D4 | Persistência | SQLite com escritor único serializado | [ADR-0003](0003-sqlite-com-escritor-unico.md) |
| D5 | Superfície | daemon HTTP; CLI é cliente | abaixo |
| D6 | Workflow | DAG em YAML com estado por nó | abaixo |
| D7 | Nomenclatura | grupo `com.accord`, artefatos `sia-*` | abaixo |
| D8 | Dado sensível | nunca persiste payload; só quem, quando e o quê | [ADR-0004](0004-nada-sensivel-em-banco-ou-log.md) |
| D9 | Segredos e assinatura | `SecretStore` por env hoje; assinatura de plugin adiada | abaixo |
| D10 | Portabilidade | nenhum código específico de SO no Core | abaixo |
| D11 | Autenticação | OIDC resource server; papéis `viewer/operator/admin` | abaixo |
| D12 | Exposição | `:8080` atrás de reverse proxy | abaixo |
| D13 | Ordem da auth | papéis e autorização agora; OIDC quando o IdP existir | abaixo |
| D14 | Primeiro plugin | `receitanet-bx` como adaptador de processo | [ADR-0001](0001-receitanet-como-plugin-java.md) |
| D15 | Execução de processo | `ProcessRunner` no Core com allowlist; plugin nunca recebe `ProcessBuilder` | [ADR-0005](0005-processo-controlado-pelo-core.md) |
| D16 | Protocolo externo | `stdio-jsonl-v1` | [ADR-0002](0002-dois-modos-de-plugin.md) |

## Decisões registradas só aqui

- **D1 — Java 23 + Maven.** Alinhado ao bot do Receitanet (bytecode 67) e dá virtual threads,
  records e `StructuredTaskScope`. Alternativa descartada: Java 21 exigiria recompilar o bot.
- **D3 — Permissões na fachada.** O `PermissionGuard` é único e compartilhado por log, storage,
  secrets e processos. `SecurityManager` não existe mais (JDK 24), então o limite é contrato, não
  sandbox. Quem precisa de isolamento real usa `kind: process`.
- **D5 — Daemon + CLI cliente.** Um único dono do banco e do scheduler. O CLI fala JSON com `/api`,
  nunca abre o SQLite.
- **D6 — Workflow em DAG.** Lista sequencial não expressa paralelismo nem retomada por nó. O estado
  por nó em `workflow_node` é o que permite continuar depois de uma queda.
- **D7 — Nomenclatura.** `com.accord` + `sia-*` desde o começo; renomear depois custa caro.
- **D9 — Segredos por ambiente; assinatura adiada.** `SIA_SECRET_<PLUGIN>_<NOME>` resolve o
  namespace sem cofre. Assinatura e registry remoto entram quando houver servidor de plugins.
- **D10 — Portabilidade.** Nenhum caminho Windows no Core, nenhum shell-out; o único ponto
  Windows-específico é o plugin do Receitanet, que declara `requires-os`.
- **D11/D13 — Auth.** `Authenticator` é a única peça que troca quando o IdP for definido; papéis,
  rotas e testes de autorização já estão prontos.
- **D12 — Reverse proxy.** TLS termina no proxy; o daemon só escuta em `127.0.0.1`.
