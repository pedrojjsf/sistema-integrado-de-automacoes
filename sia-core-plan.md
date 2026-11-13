# SIA — Sistema Integrado de Automações

> Plano de implementação (modo PLANNING — só andaime de build escrito até agora).
> `@project-planner` + skills `plan-writing` e `architecture`.
> **Revisão 4:** daemon HTTP com REST na v1, SQLite, retenção definida, regra "nada sensível no banco nem no log", multilíngue de SO.

## Goal

**SIA** é uma plataforma de automação extensível: um Core pequeno que descobre, carrega, autoriza e executa **plugins**, e orquestra `Automations` em `Workflows` — sem conhecer a lógica de nenhuma automação. Roda como **daemon HTTP**, independente de sistema operacional.

**Project Type:** `BACKEND` (serviço + biblioteca de plugin). Agente primário: `backend-specialist`.

## Ambiente — verificado

| Item | Estado | Observação |
|---|---|---|
| JDK 21 | `...\jdks\jdk-21.0.12\jdk-21.0.12` | `JAVA_HOME` (usuário) aponta para cá |
| `java` do PATH | 1.8.0_391 | `javapath` está no PATH **de máquina** e vence o de usuário — não bloqueia (Maven/IDE usam `JAVA_HOME`) |
| Maven | 3.10.0 em `C:\Maven\...` | no PATH + `mvnw` funcionando |
| SQLite | embutido (`sqlite-jdbc`) | `data/sia.db` |
| Docker/Postgres | inexistentes | **fora de escopo** |
| Git | branch `feature/automation-core` | 2 commits |

## Decisões de arquitetura

| # | Decisão | Escolha | Por quê | Alternativa descartada |
|---|---|---|---|---|
| D1 | Plataforma | **Java 21 LTS** + Maven multi-módulo | virtual threads para IO bloqueante; `record`/`sealed` para contrato | Gradle, Java 17 |
| D2 | Isolamento | **In-process**, classloader filho por plugin; `PluginHost` como costura | plugins internos; `URLClassLoader` não é sandbox — o limite é contrato, não promessa | `SecurityManager` (morto no JDK 24) |
| D3 | Permissões | **Capability-based na fachada**, deny-by-default | único ponto de interceptação confiável | análise de bytecode |
| D4 | Persistência | **SQLite** (`sqlite-jdbc`) + `SingleWriter` | máquina única, zero operação; WAL + 1 escritor serializado | Postgres (infra demais) |
| D5 | Runtime | **Daemon HTTP + REST na v1**; CLI é **cliente** do daemon | um único dono do banco e do scheduler; nenhum cliente escreve direto | CLI acessando o banco |
| D6 | Workflow | **DAG em YAML** com estado por nó | retomada e paralelismo real | lista sequencial |
| D7 | Nomenclatura | grupo `com.sia`, artefatos `sia-api/core/server/cli/testkit` | nome do produto desde o início; renomear depois custa caro | manter `automation-*` |
| D8 | **Dado sensível** | **Nunca persiste payload**; `run`/`run_log` guardam **quem, quando e o quê**, não o conteúdo | automações baixam dados pessoais; log é passivo, não histórico | confiar em redaction preguiçosa |
| D9 | Segredos | **Adiado**: `SecretStore` com implementação `env`; assinatura de plugin e registry remoto ficam para quando houver servidor de plugins | ainda não existe artefato para proteger | DPAPI (amarra ao Windows) |
| D10 | Portabilidade | nenhum código específico de SO; `java.nio` para caminhos; sem shell-out | vai rodar em Linux no servidor | atalhos Windows |

## Contratos públicos (`sia-api`)

```java
public interface AutomationPlugin {
    PluginMetadata metadata();                  // id, name, version, apiVersion, author
    List<AutomationDescriptor> automations();   // declarativo, sem IO
    void initialize(PluginContext context);     // 1x, após carga
    void shutdown();                            // idempotente, após unload
}

public interface Automation {
    String id();                                // "email.send"
    AutomationResult execute(AutomationContext ctx) throws AutomationException;
}
```

`AutomationDescriptor` declara cada automação **e o contrato de dados**:

```java
public record AutomationDescriptor(
    String id, String name, String description,
    List<ParamSpec> params,          // nome, tipo, obrigatório, SENSÍVEL, padrão
    boolean persistOutputs) {}       // default false
```

`ParamSpec.sensitive = true` é o que permite ao Core saber, sem disciplina do autor, o que jamais pode ir para log/banco. É a diferença entre política e convenção.

Outros tipos: `PluginContext` (logger, config, storage, events, secrets, clock), `AutomationContext` (runId, **actor**, params, cancel, progress, storage, events), `AutomationResult` (status, outputsKeys, mensagem), `Parameters` imutável, `CancellationToken`, `RunId`, exceções (`PluginLoadException`, `PermissionDeniedException`, `AutomationException`, `CancellationRequested`).

## Ciclo de vida

```
DISCOVERED → VALIDATED → LOADED → INITIALIZED → ENABLED ⇄ DISABLED → UNLOADED
      └──────────────┴──────────┴────────────┴──→ FAILED (isolado)
```

`shutdown()` sempre executado; falha de um plugin nunca derruba o daemon (bulkhead); estado final em `plugin_state`.

## Daemon HTTP e API REST

| Método | Rota | Função |
|---|---|---|
| GET | `/api/health` | versão, banco ok, nº de plugins, uptime → usado pelo `doctor` |
| GET | `/api/plugins` · `/api/plugins/{id}` | estado, versão, automações |
| POST | `/api/plugins/validate` | as 7 checagens, sem instalar (JAR por caminho ou multipart) |
| POST | `/api/plugins/install` · `/api/plugins/{id}/enable` · `/disable` · `/reload` | gestão |
| GET | `/api/automations` | catálogo agregado de todos os plugins |
| POST | `/api/runs` | `{automation, params, idempotencyKey}` → **202** `{runId}` |
| GET | `/api/runs` | filtros `plugin`, `status`, `from`, `limit`, `cursor` |
| GET | `/api/runs/{id}` · `/logs` · `/events` (SSE) | detalhe, log, progresso ao vivo |
| POST | `/api/runs/{id}/cancel` | cancelamento cooperativo |
| GET/POST/PATCH | `/api/schedules` | cron, intervalo, evento |
| POST | `/api/workflows/{name}/runs` · GET `/api/workflows/runs/{id}` | execução e estado do DAG |

Regras do daemon: **bind padrão `127.0.0.1:8080`** (abrir para rede é escolha explícita em config), `Authorization: Bearer <token>` com comparação em tempo constante e tokens rotulados em config (`actor` vem do rótulo), `Idempotency-Key` no `POST /api/runs` (retry do cliente não duplica execução), **nunca logar corpo de requisição**, `graceful shutdown` em SIGTERM/interrupt (para scheduler, cancela runs, `shutdown()` de plugins, fecha o banco), instância única por arquivo de lock + porta.

## Persistência SQLite e retenção

Pragmas na abertura: `journal_mode=WAL`, `foreign_keys=ON`, `busy_timeout=5000`, `synchronous=NORMAL`.

`plugin_state` · `run` (id, **actor**, plugin_id, automation_id, status, started_at, finished_at, trigger, error_code, `params_keys` só nomes) · `run_log` (run_id, ts, level, **event_code**, message sem valores) · `schedule` + `schedule_fire` (`UNIQUE(schedule_id, fire_time)`) · `workflow_run` / `workflow_node` (estado; outputs só chaves) · `kv_store` (plugin_id, key, value, `UNIQUE(plugin_id,key)`) · `retention_policy`.

**Regra D8, concreta:**
- `POST /api/runs` grava `params` **apenas** como nomes + tipo; valores de `ParamSpec.sensitive` viram `<redacted>`;
- `run_log` recebe mensagem por template (`"baixando anexo {count}"`) e nunca o conteúdo; `outputs` persistem **só as chaves**;
- `persistOutputs = true` é opt-in explícito por automação e passa por redaction de novo;
- teste obrigatório `NoSensitiveDataTest`: roda uma automação com fixture contendo CPF/e-mail/senha e faz `grep` no `.db` e no log — zero ocorrências.

**Retenção (decidida):** `run` = 1 ano · `run_log` = 90 dias · `outputs` = 30 dias (só quando opt-in) · purga diária às 03:00 **rodando como uma `Automation` do próprio SIA** · `VACUUM INTO data/backups/sia-YYYYMMDD.db` mensal, com retenção de 12 cópias.

## Estrutura de arquivos

```
ecac camila/
├── pom.xml                  # parent: com.sia, Java 21, enforcer, jacoco, spotless
├── sia-api/                 # contratos. única dep: slf4j-api
├── sia-core/                # manager, loader, engine, scheduler, bus, persistence
├── sia-server/              # daemon Javalin, composition root, graceful shutdown
├── sia-cli/                 # cliente picocli (HTTP, JSON) — não toca banco
├── sia-testkit/             # harness in-process p/ autores de plugin
├── docs/{PLUGIN_GUIDE.md, adr/, api/openapi.yaml}
├── data/                    # sia.db, backups/ (git-ignored)
└── .agents/                 # toolkit (intocado)
```

`sia-api` não depende de `sia-core`; plugins enxergam **só** `sia-api` + `sia-testkit`; `sia-cli` não depende de `sia-api` (fala só JSON — contrato por schema, não por classe compartilhada).

---

## Criar e validar um plugin — passo a passo

1. `mvn archetype:generate -DarchetypeArtifactId=sia-plugin-archetype -DpluginId=email-automation` → esqueleto com `plugin.yml`, `pom.xml` (só `sia-api`), plugin, teste.
2. `plugin.yml`: `id` (minúsculo), `version` SemVer, `api-version: 1.0`, `main`, `permissions` mínimas.
3. `AutomationPlugin`: `metadata()`, `automations()`, `initialize()`, `shutdown()`.
4. Cada `Automation`: `id`, `execute` devolvendo `AutomationResult`; **marcar `ParamSpec.sensitive`** em senha, token, CPF, e-mail.
5. Serviços só via contexto; sem `static` mutável, sem `System.out`, sem executor próprio.
6. Testar com `PluginTestKit` (in-process, SQLite em temp, sem HTTP): caminho feliz + permissão negada + **nada sensível no log**.
7. `mvn verify` no repo do plugin (teste de contrato herdado do archetype).
8. `sia-cli plugins validate ./target/email-automation-1.0.0.jar` → 7 checagens (schema, id/versão, api-version, `main` implementa o contrato, permissões usadas × declaradas via `PermissionProbe`, dependências, smoke `initialize`→`shutdown`).
9. `sia-cli plugins install ...` → `sia-cli plugins enable email-automation` → `sia-cli run email.send -p to=...` → `sia-cli runs` → `sia-cli logs <runId>`.
10. Publicar: SemVer + CHANGELOG + SHA-256; tag `email-automation-v1.0.0`. (Assinatura/registry remoto: ADR futura, D9.)

**DoD do plugin:** manifesto válido · sem `static` mutável · permissões mínimas · `sensitive` marcado onde precisa · testes com TestKit (feliz + negado) · `validate` verde · `shutdown()` idempotente sem thread vazada · `mvn verify` verde.

---

## Tasks

- [x] **T0 — Ambiente + repo** · `devops-engineer` — JDK 21, Maven 3.10 + wrapper, branch, `.gitignore`, `.gitattributes`.
- [x] **T1 — Esqueleto multi-módulo** · `backend-specialist` / `clean-code` — 6 módulos `sia-*`, enforcer, jacoco, spotless. `BUILD SUCCESS` verificado.
- [ ] **T2 — Contratos (`sia-api`)** · `backend-specialist` / `api-patterns` — interfaces/records, `ParamSpec.sensitive`, `Parameters`, `CancellationToken`, `ProgressSink`, 4 exceções; javadoc; teste de contrato (id duplicado, metadado nulo, descriptor sem `id`).
  → *Verificar:* `mvn -q -pl sia-api verify` verde; contratos sem dependência de Jackson/SQLite.
- [ ] **T3 — Manifesto + validação** · `backend-specialist` / `api-patterns` — `plugin.yml`, JSON Schema, `PluginDescriptor`, compatibilidade `api-version`, `PluginValidator` (7 checagens).
  → *Verificar:* fixtures inválidas (id maiúsculo, `main` ausente, permission desconhecida, `api-version: 2.0`) com erro por campo.
- [ ] **T4 — Discovery + loader + estados** · `backend-specialist` / `clean-code` — varredura de `plugins/`, `URLClassLoader` filho, registro, transições tabeladas, `close()` no unload.
  → *Verificar:* classe do plugin A invisível ao B; transição ilegal lança; teste de leak (`WeakReference` + threads).
- [ ] **T5 — Serviços + permissões + persistência** · `security-auditor` / `vulnerability-scanner` — logger com MDC, config tipada, `Storage` (`kv_store`), `EventPublisher`, `SecretStore` (`env`), `PermissionGuard` + `PermissionProbe`, `SingleWriter`, migrações, `NoSensitiveDataTest`.
  → *Verificar:* plugin sem `network` → `PermissionDeniedException`; storage de A não alcança chave de B; 200 escritas concorrentes sem `SQLITE_BUSY`; fixture com CPF/e-mail/senha não aparece em `.db` nem em log.
- [ ] **T6 — Execution Engine** · `backend-specialist` / `testing-patterns` — virtual threads, bulkhead por plugin, timeout, cancelamento, `RunRecord` com `actor`, retry/backoff.
  → *Verificar:* `sleep(5s)` + timeout 1s → `TIMED_OUT`; 4 concorrentes respeitam o limite; `runs` mostra duração e ator.
- [ ] **T7 — Scheduler** · `backend-specialist` / `api-patterns` — `Trigger` sealed (cron/interval/event), agenda in-process → Engine, `schedule_fire` única, misfire `skip`, `cron-utils`.
  → *Verificar:* cron `*/5 * * * * *` dispara 3x em 16s (drift < 1s); restart não duplica; sem rajada após downtime.
- [ ] **T8 — Workflow (DAG)** · `backend-specialist` + `database-architect` / `database-design` — `workflow.yml`, template de passos, ordem topológica, paralelismo, estado por nó, retomada.
  → *Verificar:* 4 passos/3 plugins concluem; matar daemon no nó 3 e reiniciar retoma do 3; ciclo falha na validação.
- [ ] **T9 — Archetype + TestKit** · `test-engineer` / `testing-patterns` — `sia-plugin-archetype`; `PluginTestKit` in-process (SQLite temp), `assertPermissionDenied`, `assertNoSensitiveDataInLogs`, teste de contrato herdado.
  → *Verificar:* archetype gera projeto que compila e passa `mvn verify` sem edição; TestKit < 20s.
- [ ] **T10 — Daemon + REST + CLI cliente** · `backend-specialist` + `devops-engineer` / `deployment-procedures` — Javalin, auth bearer, idempotência, SSE de progresso, graceful shutdown, lock de instância, `openapi.yaml`; picocli: `plugins`, `run`, `runs`, `logs`, `workflow`, `doctor`.
  → *Verificar:* `curl -H "Authorization: Bearer $TOKEN" localhost:8080/api/health` 200; sem token 401; `run` com mesma `Idempotency-Key` duas vezes = 1 execução; `kill -TERM` desliga limpo sem run órfã.
- [ ] **T11 — Exemplos + guia + hardening** · `documentation-writer` + `security-auditor` — 3 plugins (GreenMail, POI, Jsoup), `docs/PLUGIN_GUIDE.md`, ADRs D1–D10, deprecação SemVer, compatibilidade binária.
  → *Verificar:* exemplos compilam só contra `api`+`testkit`; guia executável ponta a ponta; cobertura do core ≥ 80%.

## Riscos

| Risco | Impacto | Mitigação |
|---|---|---|
| Daemon exposto além do localhost | Crítico | bind `127.0.0.1` por padrão; token obrigatório; doc explícita de que abrir exige TLS/reverse proxy |
| Log vazar dado pessoal por descuido do autor | Alto | `ParamSpec.sensitive` + `NoSensitiveDataTest` + payload nunca persistido (D8) |
| SQLite: escritor único vira gargalo | Médio | `SingleWriter` com fila, WAL, transações curtas, métrica de fila |
| `SQLITE_BUSY` por acesso concorrente (CLI, backup) | Médio | CLI **só** via HTTP; `VACUUM INTO` fora do horário; `busy_timeout` |
| `URLClassLoader` vaza thread/recurso no unload | Alto | `close()` obrigatório + teste de leak |
| Restart do daemon no meio de um run | Médio | `run` órfã marcada `INTERRUPTED` na subida; workflow retoma por nó |
| Escopo inflar (UI, registry remoto, assinatura) antes do Core | Alto | D9 adiada por decisão; registry só quando o servidor de plugins existir |

## Em aberto

1. **`groupId` definitivo** (`com.sia` é provisório) — qual o domínio reverso da empresa?
2. **Auth do REST**: token estático rotulado basta na v1, ou já precisa de usuário/SSO?
3. **Porta e bind** de produção (`8080` / `0.0.0.0` com reverse proxy?).

## Phase X — Verificação final

```bash
./mvnw -q clean verify
./mvnw -q -pl sia-server test -Dtest=ApiContractTest,AuthTest,IdempotencyTest,GracefulShutdownTest
java -jar sia-server/target/sia-server.jar &
curl -s localhost:8080/api/health
./sia-cli run email.send -p to=dev@example.com
./sia-cli runs --limit 5
./mvnw -q -pl sia-core test -Dtest=PermissionEnforcementTest,PluginIsolationTest,NoSensitiveDataTest,SqliteConcurrencyTest,WorkflowResumeTest
```

Checklist: plugins isolados ✅ · permissões deny-by-default ✅ · falha de plugin não derruba o daemon ✅ · REST autenticado e idempotente ✅ · zero dado sensível em banco/log ✅ · retenção e backup ativos ✅ · workflow retomável ✅ · cobertura ≥ 80% ✅ · guia executável ✅ · ADRs D1–D10 ✅
