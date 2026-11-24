# SIA — Sistema Integrado de Automações

> Plano de implementação (modo PLANNING — só andaime de build escrito até agora).
> `@project-planner` + skills `plan-writing` e `architecture`.
> **Revisão 8:** SIA em **Java 23** e aceita **dois modos de plugin** (`java` in-process e `process` em executável, protocolo JSON Lines) — o manifesto decide; primeiro plugin real é o **ReceitanetBX-Bot** (CLI headless); `groupId com.accord`, **SSO/OIDC na v1**, daemon em **8080 atrás de reverse proxy**, SQLite, retenção definida, regra "nada sensível no banco nem no log".

## Goal

**SIA** é uma plataforma de automação extensível: um Core pequeno que descobre, carrega, autoriza e executa **plugins**, e orquestra `Automations` em `Workflows` — sem conhecer a lógica de nenhuma automação. Roda como **daemon HTTP**, independente de sistema operacional.

**Project Type:** `BACKEND` (serviço + biblioteca de plugin). Agente primário: `backend-specialist`.

## Ambiente — verificado

| Item | Estado | Observação |
|---|---|---|
| JDK 23 | `...\jdks\jdk-23.0.2` | `JAVA_HOME` (usuário) aponta para cá; o projeto exige 23 (enforcer) |
| `java` do PATH | 1.8.0_391 | `javapath` está no PATH **de máquina** e vence o de usuário — não bloqueia (Maven/IDE usam `JAVA_HOME`) |
| Maven | 3.10.0 em `C:\Maven\...` | no PATH + `mvnw` funcionando |
| SQLite | embutido (`sqlite-jdbc`) | `data/sia.db` |
| Spotless + GJF | exige `--add-exports` no JVM do Maven | resolvido em `.mvn/jvm.config`; `spotless:check` verde |
| Docker/Postgres | inexistentes | **fora de escopo** |
| Git | branch `main` | commits pequenos por etapa |

## Decisões de arquitetura

| # | Decisão | Escolha | Por quê | Alternativa descartada |
|---|---|---|---|---|
| D1 | Plataforma | **Java 23** + Maven multi-módulo | mesmo runtime do bot (fim do atrito de bytecode); virtual threads, `record`/`sealed`, e agora `StructuredTaskScope` disponível | Java 21 (obrigaria recompilar o bot), Gradle |
| D2 | Isolamento | **Dois modos** por manifesto: `java` (in-process, classloader filho) e `process` (executável próprio, JSON Lines) atrás do mesmo contrato | o time escolhe por plugin — typed e simples vs isolado e em qualquer linguagem; `URLClassLoader` não é sandbox, então o limite é contrato | `SecurityManager` (morto no JDK 24) |
| D3 | Permissões | **Capability-based na fachada**, deny-by-default | único ponto de interceptação confiável | análise de bytecode |
| D4 | Persistência | **SQLite** (`sqlite-jdbc`) + `SingleWriter` | máquina única, zero operação; WAL + 1 escritor serializado | Postgres (infra demais) |
| D5 | Runtime | **Daemon HTTP + REST na v1**; CLI é **cliente** do daemon | um único dono do banco e do scheduler; nenhum cliente escreve direto | CLI acessando o banco |
| D6 | Workflow | **DAG em YAML** com estado por nó | retomada e paralelismo real | lista sequencial |
| D7 | Nomenclatura | grupo **`com.accord`**, pacotes `com.accord.sia.*`, artefatos `sia-api/core/auth/server/cli/testkit` | nome do produto desde o início; renomear depois custa caro | manter `automation-*` |
| D8 | **Dado sensível** | **Nunca persiste payload**; `run`/`run_log` guardam **quem, quando e o quê**, não o conteúdo | automações baixam dados pessoais; log é passivo, não histórico | confiar em redaction preguiçosa |
| D9 | Segredos | **Adiado**: `SecretStore` com implementação `env`; assinatura de plugin e registry remoto ficam para quando houver servidor de plugins | ainda não existe artefato para proteger | DPAPI (amarra ao Windows) |
| D10 | Portabilidade | nenhum código específico de SO; `java.nio` para caminhos; sem shell-out | vai rodar em Linux no servidor | atalhos Windows |
| D11 | Autenticação | **OIDC resource server**: JWT validado contra o JWKS do IdP, roles por claim, sem sessão e sem login próprio | identidade corporativa desde o início; o SIA nunca guarda senha de ninguém | token estático, tabela de usuários com senha |
| D12 | Exposição | **`:8080` atrás de reverse proxy**; TLS termina no proxy; bind local no daemon | o proxy já existe — TLS e certificado não são problema do Core | TLS direto no Javalin |
| D13 | Ordem da auth | **papéis + autorização + tokens de dev agora; OIDC quando o IdP for escolhido** | desbloqueia o resto do Core sem depender de tenant/app registration; `Authenticator` é a única peça que troca | esperar o IdP para tudo |
| D14 | Primeiro plugin | **adaptador de processo** sobre o `ReceitanetBX-Bot` | mesmo com SIA em 23 (bytecode já compatível), in-process seria ruim: fat jar de 32 MB com Derby, AWT/FlatLaf e motor oficial; o Derby tem lock exclusivo com o programa oficial; e um crash do bot não pode derrubar o daemon. Ele já expõe CLI headless completa | carregar in-process, recompilar o bot |
| D15 | Execução de processo | `ProcessRunner` **no Core** (não `ProcessBuilder` no plugin): allowlist `processes.<nome>` → **comando base** (lista de args), timeout, saída limitada, permissão `EXEC_PROCESS` | plugin `java` não escolhe o que executar; a lista de base permite trocar `exe` por `java -cp` sem tocar no plugin | dar `ProcessBuilder` cru ao plugin |
| D16 | Protocolo externo | **`stdio-jsonl-v1`**: JSON Lines em stdin/stdout (`describe`, `execute`, `cancel`, `shutdown`; `result`, `progress`, `log`, `error`) | contrato mínimo, legível, testável com `echo`; serve Python, Node, Go, shell | HTTP local (porta, auth, ciclo de vida), gRPC (dependência e geração de código) |

## Contratos públicos (`sia-api`)

```java
public interface AutomationPlugin {
    PluginMetadata metadata();                  // id, name, version, apiVersion, author
    List<AutomationDescriptor> automations();   // declarativo, sem IO
    void initialize(PluginContext context);     // 1x, após carga
    void shutdown();                            // idempotente, após unload
}

public interface Automation {
    String id();                                // "receitanet.lote"
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

Outros tipos: `PluginContext` (log, config, storage, events, secrets, **processos**, clock), `AutomationContext` (runId, **actor**, params, cancel, progress, storage, events), `AutomationResult` (status, outputsKeys, mensagem), `Parameters` imutável, `CancellationToken`, `RunId`, exceções (`PluginLoadException`, `PermissionDeniedException`, `AutomationException`, `AutomationCancelledException`, `ConfigurationException`, `InvalidPluginException`).

**Execução de processo (`ProcessRunner`)** — necessária para o primeiro plugin real:

```java
public interface ProcessRunner {
  ProcessResult run(ProcessRequest request) throws ProcessException;   // timeout, saída limitada
}

public record ProcessRequest(
    String executable,        // NOME LOGICO, resolvido pela allowlist do Core
    List<String> arguments,
    Duration timeout,
    Map<String, String> environment) {}

public record ProcessResult(int exitCode, String stdout, String stderr, Duration duration) {}
```

`executable` é **nome lógico** (`"receitanet-bx"`) mapeado em `config.yml` para o caminho real. O plugin nunca informa caminho de disco: se ele pudesse, a permissão `EXEC_PROCESS` viraria execução arbitrária. Saída é truncada em 256 KB e o Core redige valores dos parâmetros sensíveis antes de gravar.

## Dois modos de plugin (o manifesto decide)

| | `kind: java` | `kind: process` |
|---|---|---|
| Depende de `sia-api`? | **Sim** | **Não** — só do protocolo |
| Linguagem | Java (23+) | qualquer (Python, Node, Go, shell) |
| Roda onde | no processo do daemon | processo próprio, iniciado pelo Core |
| Latência por execução | ~0 | start do processo (~100 ms+) |
| Isolamento de falha | bulkhead (plugin travado não derruba o daemon, mas divide a JVM) | total: crash/OOM fica no filho |
| Acesso a serviços | direto (`Storage`, `Secrets`, `ProcessRunner`, `Log`) | via protocolo (o Core injeta config/params e recebe log/progresso) |
| Distribuição | `plugin.jar` + `plugin.yml` | pasta com binário/script + `plugin.yml` |
| Melhor para | automação de negócio em Java, precisa de tipos e de muita chamada | wrapper de ferramenta externa, protótipo rápido, time não-Java |

**Como decidir (para quem for escrever o próximo plugin):**

1. A automação já é um executável, CLI ou script? → **`process`** (foi o caso do Receitanet).
2. É código Java que vai usar `Storage`/`Secrets`/`ProcessRunner` com frequência? → **`java`**.
3. Precisa sobreviver a crash de biblioteca nativa, memória alta ou dependência pesada? → **`process`**.
4. Precisa de throughput alto (centenas de runs/min)? → **`java`** (sem custo de start).

O contrato é o mesmo nos dois: `describe`/`execute`/`cancel`/`shutdown`, `AutomationDescriptor` e `AutomationResult`. Trocar de modo depois é reescrever o adaptador, não o Core.
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

**Identidade e autorização (SSO)**

O SIA é **resource server**, não provedor de identidade: não tem tela de login, não guarda senha, não emite token de usuário.

- Token: `Authorization: Bearer <access_token>` (JWT). Validação local com `nimbus-jose-jwt`: assinatura contra **JWKS** do IdP (cache com `kid`, refresh em rotação), `iss`, `aud`, `exp`/`nbf` com tolerância de 30s de clock skew.
- `actor` = claim `sub` (estável) + `preferred_username` no `run`; papéis vêm de claim configurável (`roles.claim`, default `groups`).
- Papéis (mapeados do IdP para permissões do SIA):

| Papel | Pode |
|---|---|
| `sia.viewer` | `GET` plugins, runs, logs, workflows |
| `sia.operator` | + `POST /api/runs`, cancelar run, rodar workflow |
| `sia.admin` | + install/enable/disable/reload de plugin, CRUD de schedule |

- Sem permissão → **403**; sem token válido → **401** com `WWW-Authenticate: Bearer`.
- **Execução sem humano** (scheduler, workflow, trigger de evento) não tem token de usuário: o `run` grava `actor = system:scheduler` **e** `on_behalf_of` = `sub` de quem criou o `schedule` (coluna `schedule.owner`). Assim o histórico nunca perde a origem sem inventar identidade.
- **CLI**: `sia-cli login` usa **Authorization Code + PKCE** com redirect em loopback (`127.0.0.1:<porta efêmera>`), abre o navegador para o IdP e guarda o refresh token em `~/.sia/credentials.json` (permissão `0600`; no Windows, ACL do usuário). `--dev-token` só é aceito com `auth.mode=dev` em config, nunca por padrão.
- Modo `dev` (sem IdP, para destravar o time): token estático local, explicitamente marcado no `/api/health` como `"auth":"dev"`.

**O que o SIA precisa do IdP** (4 valores, nada mais): `issuer` · `jwks_uri` (vem do `/.well-known/openid-configuration`) · `audience`/client-id do app registrado · nome do claim de papéis + mapa valor→papel. O SIA nunca fala com o diretório do IdP; só busca chaves públicas e valida assinatura localmente.

**Claim de papéis — por que não usar `groups` de primeira:** no Entra ID `groups` devolve **GUID** de grupo (não nome), estoura em tenant grande (`_claim_names` com overage, exigindo chamada ao Graph) e pode inchar o JWT até o proxy recusar por header grande. O caminho certo é **App Role** (Entra) ou role de client/realm (Keycloak) com nomes curtos e estáveis — ex.: `sia.viewer`, `sia.operator`, `sia.admin`. `groups` fica como fallback configurável:

```yaml
auth:
  mode: oidc              # oidc | dev
  issuer: https://login.microsoftonline.com/<tenant>/v2.0
  audience: api://sia
  jwks: { ttl: 1h }
  roles:
    claim: roles          # ou groups
    map: { "sia.viewer": sia.viewer, "sia.operator": sia.operator, "sia.admin": sia.admin }
    default: none         # deny-by-default
```

**Modo `dev`** (confirmado): liberado na máquina do analista, bloqueado quando `env=prod`, anunciado no `/api/health` como `"auth":"dev"` e com warning no log de subida.

**Regras do daemon:** bind `127.0.0.1:8080` (o proxy fala com ele; abrir direto para a rede exige config explícita), confiar em `X-Forwarded-For/-Proto` **somente** do IP do proxy, `Idempotency-Key` no `POST /api/runs` (retry do cliente não duplica execução), **nunca logar corpo de requisição nem header de Authorization**, `graceful shutdown` em SIGTERM/interrupt (para scheduler, cancela runs, `shutdown()` de plugins, fecha o banco), instância única por lock file + porta.

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
├── pom.xml                  # parent com.accord: Java 23, enforcer, jacoco, spotless
├── sia-api/                 # contratos. única dep: slf4j-api
├── sia-core/                # manager, loader, engine, scheduler, bus, persistence
├── sia-auth/                # OIDC: JWKS, validação de JWT, mapeamento de roles
├── sia-server/              # daemon Javalin, composition root, graceful shutdown
├── sia-cli/                 # cliente picocli (HTTP, JSON) — não toca banco
├── sia-testkit/             # harness in-process p/ autores de plugin
├── integrations/receitanet-bx/  # primeiro plugin real (kind: java, ADR-0001)
├── docs/{PLUGIN_GUIDE.md, adr/, api/openapi.yaml}   # ADR-0001: receitanet como plugin java
├── data/                    # sia.db, backups/ (git-ignored)
└── automacao/               # binário do bot (git-ignored)
```

`sia-api` não depende de `sia-core`; plugins enxergam **só** `sia-api` + `sia-testkit`; `sia-cli` não depende de `sia-api` (fala só JSON — contrato por schema, não por classe compartilhada); `sia-auth` não conhece plugin nenhum, só identidade.

---

## Criar e validar um plugin — passo a passo

0. **Escolha o modo** (`kind: java` ou `kind: process`) pela tabela "Dois modos de plugin". O esqueleto gerado difere: `java` traz `pom.xml` + classe; `process` traz script + `plugin.yml`.
1. `mvn archetype:generate -DarchetypeArtifactId=sia-plugin-archetype -DpluginId=receitanet-bx -Dkind=java` → esqueleto com `plugin.yml`, `pom.xml` (só `sia-api`), plugin, teste.
2. `plugin.yml`: `id` (minúsculo), `version` SemVer, `api-version: 1.0`, `main`, `permissions` mínimas.
3. `AutomationPlugin`: `metadata()`, `automations()`, `initialize()`, `shutdown()`.
4. Cada `Automation`: `id`, `execute` devolvendo `AutomationResult`; **marcar `ParamSpec.sensitive`** em PIN, senha, token, CPF.
5. Serviços só via contexto; sem `static` mutável, sem `System.out`, sem executor próprio, **sem `ProcessBuilder`** (use `ctx.processes()`).
6. Testar com `PluginTestKit` (in-process, SQLite em temp, sem HTTP): caminho feliz + permissão negada + **nada sensível no log**.
7. `mvn verify` no repo do plugin (teste de contrato herdado do archetype).
8. `sia-cli plugins validate ./target/receitanet-bx-1.0.0.jar` → 7 checagens (schema, id/versão, api-version, `main` implementa o contrato, permissões usadas × declaradas via `PermissionProbe`, dependências, smoke `initialize`→`shutdown`).
9. `sia-cli plugins install ...` → `sia-cli plugins enable receitanet-bx` → `sia-cli run receitanet.lote -p arquivo=cnpjs.txt -p sistemas=1,13 -p inicio=01/01/2024 -p fim=31/01/2024` → `sia-cli runs` → `sia-cli logs <runId>`.
10. Publicar: SemVer + CHANGELOG + SHA-256; tag `receitanet-bx-v1.0.0`. (Assinatura/registry remoto: ADR futura, D9.)

**DoD do plugin:** manifesto válido · sem `static` mutável · permissões mínimas · `sensitive` marcado onde precisa · testes com TestKit (feliz + negado) · `validate` verde · `shutdown()` idempotente sem thread vazada · `mvn verify` verde.

### Plugin real: `receitanet-bx` (adaptador de processo)

O `ReceitanetBX-Bot` já é headless por CLI. O plugin é fino de propósito: monta comando, interpreta saída, devolve resultado. Nada de regra de negócio duplicada.

| Automação | Comando do bot |
|---|---|
| `receitanet.diag` | `diag` (ambiente, banco Derby, certificados) |
| `receitanet.catalogo` | `catalogo` (sincroniza sistemas/papéis) |
| `receitanet.lote` | `lote-cnpj --arquivo --sistemas --inicio --fim [--baixar] [--limite-cnpjs] [--dry-run]` |
| `receitanet.pedidos` | `pedidos --filtro --limite` |
| `receitanet.pendentes` | `pendentes` |
| `receitanet.baixar` | `baixar --pedido <id>` ou `baixar --todos` |

Parâmetros: `arquivo` (caminho da lista), `sistemas` (CSV de ids), `inicio`/`fim` (`dd/MM/aaaa`, validadas **antes** de gerar processo), `baixar` (bool), `limiteCnpjs` (int), `dryRun` (bool) e `ni` (opcional, filtra o certificado). **Sem parâmetro de PIN**: a autenticação usa o certificado do repositório do Windows.

Duas ressalvas que o plugin precisa documentar: o bot e o Receitanet oficial usam o **mesmo banco Derby** (só um abre por vez), e o certificado vem do repositório do Windows (`--ni`), nunca de `--pin` na linha de comando (visível na lista de processos).

---

## Tasks

- [x] **T0 — Ambiente + repo** · `devops-engineer` — JDK 23, Maven 3.10 + wrapper, branch, `.gitignore`, `.gitattributes`.
- [x] **T1 — Esqueleto multi-módulo** · `backend-specialist` / `clean-code` — 7 módulos `sia-*` (incl. `sia-auth`), enforcer, jacoco, spotless. `BUILD SUCCESS` verificado.
- [x] **T2 — Contratos (`sia-api`)** · `backend-specialist` / `api-patterns` — `AutomationPlugin`, `Automation`, `AutomationDescriptor`, `ParamSpec` (com `sensitive`), `PluginContext`, `AutomationContext` (runId/actor/params/serviços), `AutomationResult` (só chaves de saída), `Parameters` tipado, fachadas `Log`/`Storage`/`SecretAccess`/`EventPublisher`/`ProgressSink`/`CancellationToken`/`Configuration`, 6 exceções e `PluginContract` validando por campo. **26 testes verdes.**
  → *Verificado:* `mvn -pl sia-api test` = 26/26; `dependency:tree` do api segue só com `slf4j-api`; contrato rejeita metadata nula, id com maiúscula, `apiVersion: 2.0`, automação duplicada e parâmetro sensível com valor padrão.
- [x] **T3 — Manifesto + validação** · `backend-specialist` / `api-patterns` — `PluginManifest` com `kind` (`java`/`process`), `PluginKind`, `PluginPermission` no `sia-api`, JSON Schema em `sia-core/src/main/resources/schemas/`, validação estrita (campo desconhecido é erro) com mensagem por campo. **18 testes verdes.**
  → *Verificado:* `mvn -pl sia-core -am test` = 18/18; manifesto `process` do receitanet aceito com protocolo padrão; rejeita campo com typo, id maiúsculo, `api-version: 2.0`, `main` faltando no `java`, `command` absoluto ou com `..`, permissão desconhecida, dependência de si mesmo e `requires-os` inválido.
- [x] **T4 — Discovery + loader + estados** · `backend-specialist` / `clean-code` — `PluginDiscovery` (pasta = `plugin.yml` + 1 JAR), `PluginClassLoader` filho-primeiro com pacotes compartilhados (`sia-api`, `slf4j`), `PluginLoader`, `LoadedPlugin` com máquina de estados, `PluginRegistry` com detecção de duplicata e `unloadAll`. **10 testes verdes** (fixtures compiladas em tempo de teste contra o `sia-api`, não no classpath do teste).
  → *Verificado:* classe do plugin beta invisível ao alpha; transição `LOADED → DISABLED` lança; `unload` chama `shutdown()` e fecha o JAR (classe não carregada antes falha depois); duplicata recusada; `kind: process` rejeitado com mensagem explícita até o T4c.
- [x] **T5 — Serviços + permissões + persistência** · `security-auditor` / `vulnerability-scanner` — `Database` (WAL, `busy_timeout`, `foreign_keys`), `SchemaMigrator` (`V1__init.sql`, `user_version`), `SingleWriter` (fila única + retry em `SQLITE_BUSY`), `SqliteStorage` (namespaced, chave sem caminho), `MapConfiguration`, `Slf4jLog` (MDC `pluginId`), `EnvSecretAccess` (escopo por plugin, exige `secrets`), `PermissionGuard`/`PermissionProbe`, `InProcessEventBus`, `PluginContextFactory`. **29 testes novos (55 no core).**
  → *Verificado:* plugin sem `storage`/`secrets`/`process` recebe `PermissionDeniedException`; `alpha` e `beta` gravam a mesma chave sem se ver; chave com `/`, `\\` ou `..` é rejeitada; 200 escritas concorrentes de 10 threads sem `SQLITE_BUSY`; migração idempotente. `NoSensitiveDataTest` fica na T6, porque só lá existe persistência de `run`.
- [x] **T6 — Execution Engine** · `backend-specialist` / `testing-patterns` — `Automation.describe()` (descrição e implementação no mesmo objeto), `ExecutionEngine` com virtual threads, bulkhead por plugin (fila configurável), timeout com cancelamento do token + interrupção, `cancel`/`cancelAll`, `RunRepository` (`run` + `run_log`, migração V2), `Redactor` por valor, `RunLog`/`RunProgress`, `NoSensitiveDataTest`. **13 testes novos (67 no core).**
  → *Verificado:* timeout de 300 ms em automação de 10 s → `TIMED_OUT`; `cancelAll()` → `CANCELLED`; 2ª execução com 1 vaga e fila de 150 ms → `REJECTED`/`CONCURRENCY_LIMIT`; plugin `INITIALIZED` é recusado; fixture com CPF e protocolo não aparece em `.db`, `-wal` nem no log (só `***`). Retry/backoff fica na T7/T8, onde a repetição é decisão de agenda/workflow, não do run unitário.
- [x] **T7 — Scheduler** · `backend-specialist` / `api-patterns` — `Trigger` sealed (`cron` com `cron-utils` QUARTZ, `interval`, `event`), `Schedule`/`ScheduleRepository` (migração V4: `params` e `run_id`), `SchedulerService` desacoplado do engine por `Launcher`, `schedule_fire` com chave única por horário, misfire `skip` (horário perdido pula, nunca rajada), `POST/GET/DELETE /api/schedules` + `enable|disable`. **11 testes novos.**
  → *Verificado ao vivo:* agendamento de 3 s executou **6 vezes sozinho** com `trigger=schedule` e `actor=system:scheduler`; `nextFireAt` avançou a cada disparo; desabilitar parou os disparos; horário perdido é pulado sem rajada; mesmo horário não dispara duas vezes.
  → *Nota:* `cron` normaliza `* * * * * *` para `* * * * * ?` (Quartz exige `?` em um campo de dia) em vez de recusar a expressão.
- [x] **T8 — Workflow (DAG)** · `backend-specialist` + `database-architect` / `database-design` — `workflow.yml` (`WorkflowDefinition`/`WorkflowStep`), `WorkflowLoader` com validação (id duplicado, dependência inexistente, auto-dependência, ciclo, template quebrado, formato de `use`), `Templating` (`${inputs.x}`, `${steps.s.outputs.k}`), `WorkflowEngine` (ondas paralelas, `onError` FAIL/CONTINUE/RETRY com `retries`, estado por nó, retomada pulando o que já foi feito), `WorkflowRepository` (migração V5: definição e entradas persistidas), wiring no `SiaCore` e rotas REST (`GET /api/workflows`, `POST /api/workflows/{name}/runs`, `GET /api/workflows/runs[/{id}]`, `POST .../resume`). **15 testes novos.**
  → *Verificado ao vivo:* `POST /api/workflows/exemplo-receitanet/runs` → `{"status":"SUCCESS","nodes":{"diagnostico":"DONE"}}`.
  → *Verificado em teste:* ordem e paralelismo, template entre passos, FAIL interrompe e marca `SKIPPED`, CONTINUE gera `PARTIAL`, RETRY tenta 3x, ciclo recusado na validação, retomada não reexecuta nó concluído.
  → *Pendência honesta:* o cenário "matar o daemon no nó 3" foi coberto por teste de unidade sobre o estado persistido, não por kill real — falta um teste de caos (fica na T12).
- [x] **T9 — Archetype + TestKit** · `test-engineer` / `testing-patterns` — `sia-plugin-archetype` (jar com `archetype-metadata.xml` + templates: pom standalone, `plugin.yml`, plugin, automação e teste) e `PluginTestKit` (`start(plugin[, permissões][, config])`, `run(...)` com `RunResult`, `assertSuccess`, `assertFailure(código)`, `assertNoSensitiveData`, storage SQLite temporário, `Permissions.none()/of()/all()`). **6 testes novos.**
  → *Verificado ponta a ponta:* `mvn archetype:generate -DarchetypeArtifactId=sia-plugin-archetype ...` gerou `demo-plugin` com a estrutura correta (pom, plugin.yml, sources no pacote, teste); o projeto gerado compila e passa `mvn test` **sem edição** (`Tests run: 1`).
  → *Bug encontrado pelo TestKit:* a permissão `STORAGE` era declarada no manifesto mas **não era exigida** — `SqliteStorage` agora chama `PermissionGuard` em `get/put/delete/keys`. Sem isso, o `TestKit` “provava” isolamento que não existia.
- [x] **T10a — Daemon + REST núcleo + CLI** · `backend-specialist` + `devops-engineer` — `SiaCore` (composition root: banco, plugins, engine), `SiaConfig`/`ConfigLoader` (YAML estrito, `${java.home}`/`${env:VAR}`), `SiaServer` (Javalin) com `health`, `plugins`, `automations`, `POST /api/runs` (202 + id), `runs`, `runs/{id}`, `logs`, `cancel`; bearer token + papel por rota (viewer/operator/admin); `sia-cli` com `doctor`, `plugins`, `automations`, `run` (com polling), `runs`, `logs`, `cancel`. **11 testes novos (server 7, config 4).**
  → *Verificado ao vivo:* `GET /api/health` sem token → **401**; com token → `{"status":"ok","auth":"dev","plugins":1}`; `POST /api/runs` → 202 com id; `receitanet.diag` → `SUCCESS` em ~6 s com `outputs=["sistemas"]`; `sia-cli run receitanet.diag` imprimiu `status SUCCESS`; `runs` listou as execuções; automação inexistente → 400; `viewer` executando → 403.
- [x] **T10b — Superfície REST restante** · `backend-specialist` — `Idempotency-Key` (tabela V3, registro antes do disparo), SSE em `/api/runs/{id}/events` (`ready`, `log`, `progress`, `step`, `ping`, `finished`), `plugins/validate|install` + `enable|disable|reload`, lock de instância por banco (`sia.db.lock`), `openapi.yaml` servido em `/openapi.yaml`. **12 testes novos (SiaCore 9, eventos 3).**
  → *Verificado ao vivo:* dois POST com a mesma chave devolveram o **mesmo runId**; `plugins/validate` respondeu `{"id":"receitanet-bx","kind":"java","mainClassPresent":true}`; SSE entregou `event: ready` e `event: finished {"status":"SUCCESS"}`; segundo daemon no mesmo banco é recusado com "ja existe um SIA rodando".
  → *Adiado com motivo:* CRUD de `schedules` e `workflow run` dependem de T7/T8; rate limit fica para quando houver cliente externo real; `X-Forwarded-For` não é usado hoje (o daemon não registra IP).
- [x] **T11 — Papéis e autorização** · `security-auditor` — `SiaRole` (VIEWER/OPERATOR/ADMIN → permissões), `Principal` (user/system), `RoleMapping` (App Roles + fallback para GUID de grupo), `Authorizer` (401/403), `DevTokens` (comparação em tempo constante, proibido em `prod`). **17 testes verdes.**
  → *Verificado:* `mvn -pl sia-auth -am test` = 17/17; `ADMIN` cobre todas as permissões varrendo o enum; claim desconhecido não escala privilégio.
''''''- [x] **T19 — Catálogo por plugin (aba única)** — a aba Automações passou a mostrar **um cartão por plugin** em vez de um por automação; as operações ficam dentro do painel (`sia:init` entrega `automations` completas). `pluginView` expõe `description`; cartões alinhados com ação no rodapé e pílulas sem quebra.
  → *Verificado:* Receitanet aparece como um cartão "6 operações" com chips (diag, catalogo, lote, pedidos, pendentes, baixar); abrir o painel executa a operação escolhida e devolve o resultado.
- [x] **T18 — Payloads de requisição tipados**' — `openapi.yaml` ganhou schemas de entrada (`RunRequest`, `ScheduleRequest`, `WorkflowRunRequest`, `PluginPathRequest`) e de saída (`RunAccepted`, `RunWaited`, `PluginValidation`), todos referenciados nos paths; `types/api.d.ts` é regerado a partir deles. `app.js` usa helpers tipados (`submitRun`, `runAndWait`, `postSchedule`, `postWorkflowRun`) e `tsc --strict` passa com 0 erros.
  → *Verificado:* teste negativo (`automate` no lugar de `automation`) falha com "does not exist in type RunRequest"; E2E do painel continua verde.
- [x] **T17 — Interface própria do plugin**' — `ui: {entry,width,height}` no manifesto, rota `GET /api/plugins/{id}/ui/*` com leitura restrita à pasta declarada (jar ou diretório, 5 MB, 400 para `..`), painel em `iframe` sandbox com ponte `postMessage` (`sia:init`, `sia:execute`, `sia:result`, `sia:close`), execução `POST /api/runs?wait=true` devolvendo saídas **em memória** para o painel sem persistir valores. `hello-plugin` ganhou `ui/panel.html` de exemplo. Docs: [PLUGIN_UI.md](docs/PLUGIN_UI.md), [PROCESS_PLUGIN_PROTOCOL.md](docs/PROCESS_PLUGIN_PROTOCOL.md), [PLUGIN_COOKBOOK.md](docs/PLUGIN_COOKBOOK.md), ADR-0006.
  → *Verificado:* `hello-plugin` instalado serve o painel (200, 5 KB), traversal devolve 400, `wait=true` devolve `outputs.saudacao` com valor enquanto o histórico mantém só a chave; 7 testes novos.
- [x] **T16 — Decisões de operação local**' — `auth.mode: none` (sem login; autenticação dentro das automações, ex.: certificado digital) aceito **somente** com bind em loopback, `scheduler.zone` com padrão `America/Sao_Paulo` (cron e horários da API com offset `-03:00`), OpenAPI cobrindo **26 paths / 28 operações** (verificado por parser) e payload inválido respondendo **400** em vez de 500.
  → *Verificado ao vivo:* `/api/health` responde sem token (`auth: "none"`), interface abre direto no painel, `POST /api/schedules` com payload inválido devolve 400, agenda criada com `nextFireAt` em `-03:00`.
  → *Decisão do usuário:* login não faz parte do SIA; servidor próprio e assinatura de plugin ficam pausados.
- [x] **T15 — Interface web para usuário comum**' · `frontend-design` + `design-spec` — DESIGN.md com tokens, SPA sem build servida pelo Javalin em `/` (login por chave, Painel, Automações com formulário dinâmico a partir de `ParamSpec`, Histórico com log em drawer, Agendas, Fluxos, Plugins), tradução de `errorCode` em frase com próxima ação, log também em arquivo.
  → *Verificado:* `GET /` → 302 para `/index.html`; `/index.html`, `/styles.css` e `/app.js` respondem 200; teste de servidor garante a rota; API responde com token.
  → *Limites desta versão:* login por chave (sem SSO), sem upload de plugin pela UI, sem editor de workflow, sem criação de plugin.
- [x] **T14 — Correções de ciclo de vida de plugin**' — estado `enable`/`disable` persistido em `plugin_state` (restaurado na subida), `POST /api/plugins/{id}/uninstall` (descarrega, apaga pacote e remove estado), e erro de estado (`plugin não ENABLED`) mapeado para **409** com mensagem clara em vez de 500. `LoadedPlugin` passou a guardar a pasta do pacote para uso em reload/uninstall. **4 testes novos.**
  → *Verificado ao vivo:* plugin desabilitado continua `DISABLED` após reiniciar o daemon; `POST /api/runs` nesse estado devolve `409 {"error":"plugin receitanet-bx nao esta ENABLED (estado: DISABLED)"}`; `uninstall` removeu registro e pasta, e `install` pelo JAR recriou o plugin `ENABLED`.
- [ ] **T11b — OIDC/JWKS (adiado por decisão)**' · `security-auditor` — implementar o `Authenticator` OIDC: JWKS com cache por `kid`, `iss`/`aud`/`exp`, `sia-cli login` (code + PKCE loopback) e guarda de credencial `0600`. Só a autenticação muda; `Authorizer`, papéis e rotas ficam intactos.
  → *Verificar:* token ausente → 401; `viewer` em `POST /api/runs` → 403; assinatura inválida → 401; login completo contra Keycloak no CI.
- [x] **T4b — Execução de processo** · `security-auditor` / `vulnerability-scanner` — `ProcessRunner`/`ProcessRequest`/`ProcessResult`/`ProcessException` no `sia-api`, `PluginContext.processes()`, `ProcessAllowlist` (nome lógico → comando base + timeout padrão), `DefaultProcessRunner` com permissão `process`, drenagem paralela de stdout/stderr, corte em 256 KB, redaction de valores, kill de árvore no timeout. **7 testes verdes.**
  → *Verificado:* `mvn -pl sia-core -am test` = 61 no total; executável fora da allowlist → `NOT_ALLOWED` antes de subir; `sleep 30s` com timeout 600 ms morre e retorna `timedOut`; valor sensível vira `***` na saída; plugin sem a permissão recebe `PermissionDeniedException`.
- [x] **T4c — Host de plugin de processo** · `backend-specialist` / `api-patterns` — `stdio-jsonl-v1` em `ProcessPluginHost` (uma linha = um JSON, leitura por thread dedicada, fila de respostas, `__eof` quando o processo morre), `ProcessAutomationPlugin` (adaptador que faz `kind: process` passar pelo mesmo contrato de `AutomationPlugin`), `ProcessAutomation` (envia `execute`, traduz `describe`/`result`/`error`, encaminha `log`/`progress` para o contexto do run), `PluginLoader` decidindo por `kind`, `LoadedPlugin` aceitando ausência de classloader. **2 testes novos (carga+execução real de um plugin de processo em Java puro).**
  → *Verificado:* plugin de processo escrito como `main` Java sem nenhuma dependência do SIA respondeu ao `describe`, executou `process.eco` via engine e devolveu `SUCCESS` com log encaminhado; plugin que morre antes do handshake é recusado com `PluginLoadException`.
  → *Limite conhecido:* o host serializa execuções por plugin (o protocolo v1 não multiplexa runs no mesmo processo); paralelismo entre plugins continua normal. Multiplexação fica para o v2 do protocolo.
- [x] **T13 — Plugin `receitanet-bx`** (ADR-0001: `kind: java`) · `backend-specialist` + `test-engineer` — módulo `integrations/receitanet-bx`: 6 automações (`diag`, `catalogo`, `lote`, `pedidos`, `pendentes`, `baixar`), validação de data/arquivo/sistemas **antes** de subir processo, tradução de erros do Derby (`XSDB6` → banco ocupado, `42818`/`42S02` → incompatível, `XJ040` → inacessível), `diag` que aceita exit `-1` com saída saudável, sem PIN (certificado do Windows), `plugin.yml` com `permissions: [process]` e `requires-os: [windows]`. **22 testes unitários + 1 IT real.**
  → *Verificado:* `ReceitanetDiagIT` executou o bot real pelo SIA e retornou `SUCCESS` em ~4 s; data `31/04` falha em milissegundos sem subir processo; `baixar` recusa `pedido` e `todos` juntos. Duas adições de contrato entraram: `ProcessRequest.workingDirectory` (o bot grava `bot.log`/`erros/` no cwd) e `AutomationContext.processes()`.
- [x] **T12 — Guia + segundo exemplo + hardening** · `documentation-writer` + `security-auditor` — `docs/PLUGIN_GUIDE.md` (10 seções, do archetype ao troubleshooting, com o receitanet como caso real), `docs/adr/` (índice D1–D16 + ADRs 0002–0005), `examples/hello-plugin` (kind java, 3 testes com TestKit), `README.md` com quick start, gate de cobertura no `sia-core` (`jacoco:check` linha ≥ 80%), teste de caos do workflow (daemon morto no passo 2 → retomada só do passo 2).
  → *Verificado:* cobertura do core **80,1%** com o gate ativo; `WorkflowCrashResumeTest` fecha o engine no meio do passo e a retomada executa apenas `p.b`; `hello-plugin` compila só contra `api`+`testkit` e passa.
  → *Pendências registradas:* `T11b` (OIDC) segue bloqueada pela escolha do IdP; `sia-cli plugin install` não existe (instalação só por REST); assinatura de plugin (D9) continua adiada.

### Ordem sugerida para concluir esta parte

```
T8  →  T9  →  T4c  →  T11b  →  T12
```

| Ordem | Task | Por que agora | Bloqueio |
|---|---|---|---|
| 1º | **T8 — Workflow (DAG)** | é o que transforma "executor de automações" em plataforma: encadear `baixar → classificar → mover` com retomada por nó. Reusa engine, schedule e eventos já prontos | nenhum |
| 2º | **T9 — Archetype + TestKit** | só faz sentido depois do workflow, porque o `PluginTestKit` precisa conseguir testar um plugin dentro de um passo de workflow; e antes de o time começar a escrever plugins em série | T8 (para o harness completo) |
| 3º | **T4c — Host de plugin `process`** | completa a promessa do manifesto (`kind: process`). Nada hoje depende disso, mas prometer dois modos e entregar um é dívida aberta | nenhum |
| 4º | **T11b — OIDC/JWKS** | quando o IdP existir. O `Authenticator` é a única peça que troca; papéis, rotas e testes ficam intactos | decisão do IdP |
| 5º | **T12 — Guia + hardening** | fecha o ciclo: `docs/PLUGIN_GUIDE.md` com o receitanet como caso real, segundo exemplo mínimo, cobertura ≥ 80%, ADRs restantes | T8/T9 (guia cita workflow e testkit) |

**Se T11b destravar antes** (IdP escolhido), ela pode entrar em qualquer ponto entre T9 e T12 — não depende de código novo, só de configuração e de um tenant.

**Se o time for começar a escrever plugins antes da T8**, inverta T9 e T8: o TestKit é o que destrava outras pessoas, e ele não depende do workflow para o caso simples (plugin → automação → asserção).

Depois da T12 esta parte do projeto está fechada. O que fica fora, por decisão registrada: registry remoto com assinatura de plugin (D9), UI web e execução multi-máquina.

## Riscos

| Risco | Impacto | Mitigação |
|---|---|---|
| Daemon exposto além do proxy | Crítico | bind `127.0.0.1`; o proxy é o único caminho; OIDC obrigatório fora de `dev` |
| IdP indisponível / JWKS fora do ar | Alto | cache de JWKS com TTL longo e rotação por `kid`; modo degradado somente leitura |
| Papel mal mapeado = escalada de privilégio | Alto | policy deny-by-default por rota; teste por papel; claim configurável por ambiente |
| Refresh token no disco do analista | Médio | arquivo `0600`, rotação, `sia-cli logout`, nunca em log |
| Log vazar dado pessoal por descuido do autor | Alto | `ParamSpec.sensitive` + `NoSensitiveDataTest` + payload nunca persistido (D8) |
| SQLite: escritor único vira gargalo | Médio | `SingleWriter` com fila, WAL, transações curtas, métrica de fila |
| `SQLITE_BUSY` por acesso concorrente (CLI, backup) | Médio | CLI **só** via HTTP; `VACUUM INTO` fora do horário; `busy_timeout` |
| `URLClassLoader` vaza thread/recurso no unload | Alto | `close()` obrigatório + teste de leak |
| Restart do daemon no meio de um run | Médio | `run` órfã marcada `INTERRUPTED` na subida; workflow retoma por nó |
| Escopo inflar (UI, registry remoto, assinatura) antes do Core | Alto | D9 adiada por decisão; registry só quando o servidor de plugins existir |
| Processo filho esquecido (órfão) ou PID vazado | Alto | `ProcessRunner` mata a árvore no timeout e no cancelamento; teste com `sleep` |
| Certificado A1/token exigir PIN interativo | Médio | operação usa o repositório do Windows (`--ni`); se um token exigir PIN, avaliar `--pkcs11` com prompt fora do SIA |
| Bot x Receitanet oficial no mesmo banco Derby | Médio | lock de instância já existe no bot; plugin reporta `XSDB6`/`42818` como `errorCode` estável |
| Plugin exigir Windows (jpackage/ICP-Brasil) | Médio | `requiresOs` no manifesto; plugin falha com mensagem clara fora do Windows em vez de erro obscuro |

## Em aberto

1. **IdP**: adiado por decisão — quando definir, preciso de `issuer`, `audience` e se o tenant tem App Roles (senão, mapa de `groups` → papel).
2. **App registration**: quem cria o app do SIA no IdP (redirect `http://127.0.0.1:*`, escopo `api://sia/.default`)?
3. **Comando base do bot**: `java -cp <jar> numberia.bot.Inicio` com o JDK do SIA (exit code confiável e `-Djava.awt.headless=true`) ou o `ReceitanetBX-Bot.exe` empacotado? Ambos cabem na allowlist; a escolha é qual vai para `config.yml` em produção. O manifesto do bot (lido do próprio JAR) exige, para rodar via `java`, as flags `--add-opens=java.base/java.security=ALL-UNNAMED` e `--add-exports=jdk.crypto.mscapi/sun.security.mscapi=ALL-UNNAMED` — sem elas o certificado do Windows não abre.

## Phase X — Verificação final

```bash
./mvnw -q clean verify
./mvnw -q -pl sia-auth test -Dtest=RoleMappingTest,AuthorizerTest,DevTokensTest
./mvnw -q -pl sia-server test -Dtest=ApiContractTest,IdempotencyTest,GracefulShutdownTest
java -jar sia-server/target/sia-server.jar --config config/local.yml &
curl -s localhost:8080/api/health
TOKEN=$(./sia-cli login --print-token)   # ou token do IdP
curl -s -H "Authorization: Bearer $TOKEN" localhost:8080/api/plugins
java -jar sia-cli/target/sia-cli.jar run receitanet.diag
java -jar sia-cli/target/sia-cli.jar runs --limit 5
./mvnw -q -pl sia-core test -Dtest=PermissionEnforcementTest,PluginIsolationTest,NoSensitiveDataTest,SqliteConcurrencyTest,WorkflowResumeTest
```

Checklist: plugins isolados ✅ · permissões deny-by-default ✅ · falha de plugin não derruba o daemon ✅ · REST autenticado por SSO e idempotente ✅ · 401/403 por papel testados ✅ · zero dado sensível em banco/log ✅ · retenção e backup ativos ✅ · workflow retomável ✅ · cobertura ≥ 80% ✅ · guia executável ✅ · ADRs D1–D12 ✅
