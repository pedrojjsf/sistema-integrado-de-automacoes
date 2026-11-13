# Automation Core — Plataforma de Automação Extensível em Java

> Plano de implementação (modo PLANNING — nenhum código de domínio escrito).
> Gerado por `@project-planner` + skills `plan-writing` e `architecture`.
> **Revisão 3:** SQLite no lugar de Postgres, ambiente verificado, escopo = Core.

## Goal

Construir um **Core** pequeno e estável que descobre, carrega, autoriza e executa **plugins** — e orquestra `Automations` em `Workflows` — sem conhecer a lógica de nenhuma automação. Plugins são de autoria interna, então o entregável inclui **um caminho reprodutível de criar, testar e validar plugins**.

**Project Type:** `BACKEND` (biblioteca + runtime CLI). Agente primário: `backend-specialist`.

## Ambiente — verificado nesta máquina

| Item | Estado real | Observação |
|---|---|---|
| Java 21 | `C:\Users\joaopedro00\Documents\jdks\jdk-21.0.12\jdk-21.0.12` — **ok** | `JAVA_HOME` não estava definido em lugar nenhum |
| `java` do PATH | **1.8.0_391** | `javapath` do Oracle JRE 8 está no PATH **de sistema**, que precede o PATH de usuário → por isso "não aponta" para o 21 |
| Correção aplicada | `JAVA_HOME` (usuário) = JDK 21 | Maven, IDE e `mvnw` passam a usar 21; shells novos herdam |
| Maven | **3.10.0** em `C:\Maven\apache-maven-3.10.0-bin\apache-maven-3.10.0\bin` | adicionado ao PATH de usuário; `mvn -v` reporta `Java 21.0.12` |
| Docker / Postgres | ausentes | **não são mais necessários** |
| SQLite | embutido via `sqlite-jdbc` | zero instalação, arquivo `data/automation.db` |
| Git | repo iniciado, branch **`feature/automation-core`** | primeiro commit pendente |

> Nota operacional: neste terminal o `java` do PATH continua sendo o 8 (PATH de sistema vence). Para trocar `java` globalmente seria preciso editar o PATH **de máquina** (admin). Não é bloqueio: `JAVA_HOME` + Maven Wrapper resolvem tudo que o projeto usa.

## Decisões de arquitetura (ADR resumido)

| # | Decisão | Escolha | Por quê | Alternativa descartada |
|---|---------|---------|---------|------------------------|
| D1 | Plataforma | **Java 21 LTS** + **Maven multi-módulo** | Virtual threads para IO bloqueante, `sealed`/`record` para contratos fechados; `mvn verify` único | Gradle, Java 17 |
| D2 | Isolamento | **In-process**, classloader filho por plugin; `PluginHost` como costura | Plugins são do time → confiáveis; `URLClassLoader` **não é sandbox**, então o limite vira contrato, não promessa | `SecurityManager` (desativado no JDK 24) |
| D3 | Permissões | **Capability-based na fachada**, deny-by-default | Único ponto de interceptação confiável; testável sem JVM especial | Análise de bytecode |
| D4 | Persistência | **SQLite** (`sqlite-jdbc`) + WAL, escritor único serializado | Ferramenta interna de máquina única: histórico, agenda e retomada sem operar servidor | Postgres (infra demais para 1 máquina), JSON em disco (sem transação) |
| D5 | Superfície | **CLI (picocli)** na v1 | Valida o Core antes de expor rede | REST/Spring na v1 |
| D6 | Workflow | **DAG em YAML** com estado por nó persistido | Retomada e paralelismo real | Lista sequencial |

## Contratos públicos (`automation-api`)

```java
public interface AutomationPlugin {
    PluginMetadata metadata();                  // id, name, version, apiVersion, author
    List<AutomationDescriptor> automations();   // declarativo, sem IO
    void initialize(PluginContext context);     // 1x, após carga
    void shutdown();                            // idempotente, após unload
}

public interface Automation {
    String id();                                        // "email.send"
    AutomationResult execute(AutomationContext ctx) throws AutomationException;
}

public record AutomationContext(
    RunId runId, String pluginId, Parameters params,
    Logger log, ProgressSink progress,
    CancellationToken cancellation, Storage storage,
    EventPublisher events, SecretAccess secrets) {}
```

Três refinamentos sobre a proposta original: `execute` sai do plugin (senão um plugin não pode expor N automações); `Automation` **retorna** `AutomationResult` com `outputs` (senão Workflow não encadeia); `PluginContext` expõe fachadas estreitas com checagem de permissão em vez do Core.

## Ciclo de vida

```
DISCOVERED → VALIDATED → LOADED → INITIALIZED → ENABLED ⇄ DISABLED → UNLOADED
      └──────────────┴──────────┴────────────┴──→ FAILED (isolado)
```

Invariantes: transições validadas por tabela; `shutdown()` sempre executado; falha de um plugin nunca derruba o Core (bulkhead); estado final em `plugin_state`.

## Schema SQLite (migração `V1__init.sql`)

Pragmas na abertura: `journal_mode=WAL`, `foreign_keys=ON`, `busy_timeout=5000`, `synchronous=NORMAL`.

`plugin_state` (id, version, state, loaded_at, error) · `run` (id, plugin_id, automation_id, status, started_at, finished_at, trigger, error, outputs TEXT/json) · `run_log` (run_id, ts, level, message) · `schedule` (id, trigger TEXT/json, target, enabled, next_fire_at) · `schedule_fire` (schedule_id, fire_time, `UNIQUE(schedule_id, fire_time)`) · `workflow_run` / `workflow_node` (estado e outputs por nó) · `kv_store` (plugin_id, key, value TEXT/json, `UNIQUE(plugin_id,key)`) · `secret` (plugin_id, name, ciphertext).

**Concorrência (SQLite tem 1 escritor):** todas as escritas passam por um `SingleWriter` (1 thread de plataforma + fila), leituras usam pool de conexões read-only. Chamadas do plugin são `async` sobre virtual threads e nunca seguram transação durante IO externo. Migração: Flyway (`flyway-database-sqlite`) ou migrador próprio de ~40 linhas — decidir na T5 conforme atrito de versão.

## Estrutura de arquivos

```
ecac camila/
├── pom.xml                        # parent: Java 21, enforcer, spotless, jacoco
├── automation-api/                # contratos + exceções. Única dep: slf4j-api
├── automation-core/               # loader, lifecycle, engine, scheduler, bus, persistence
├── automation-cli/                # picocli, composition root
├── automation-testkit/            # harness in-memory + SQLite temporário
├── automation-plugin-archetype/   # scaffolding mvn archetype:generate
├── examples/                      # email-plugin, spreadsheet-plugin, scraper-plugin
├── docs/
│   ├── PLUGIN_GUIDE.md            # o passo a passo da seção seguinte
│   └── adr/                       # D1–D6
├── data/                          # automation.db (git-ignored)
├── mvnw / mvnw.cmd / .mvn/
└── .agents/                       # toolkit (intocado)
```

Regra de ouro: **`automation-api` não depende de `automation-core`**; plugins enxergam só `automation-api` + `automation-testkit`.

---

## Criar e validar um plugin — passo a passo

Este é o fluxo que vira `docs/PLUGIN_GUIDE.md` (T11), sustentado por ferramenta em T9/T10.

### 1. Gerar o esqueleto

```bash
mvn archetype:generate -DarchetypeGroupId=com.example.automation \
  -DarchetypeArtifactId=automation-plugin-archetype \
  -DarchetypeVersion=1.0.0 -DpluginId=email-automation -DgroupId=com.example.email
```

### 2. Estrutura gerada

```
email-automation/
├── plugin.yml                    # manifesto
├── pom.xml                       # só automation-api + automation-testkit (test)
└── src/main/java/com/example/email/EmailPlugin.java
    src/test/java/com/example/email/EmailPluginTest.java
```

### 3. Preencher o manifesto

```yaml
id: email-automation          # ^[a-z][a-z0-9-]{2,63}$ — sem maiúsculas
name: Email Automation
version: 1.0.0                # SemVer
api-version: 1.0              # compatível com Core 1.x
author: Time de Automação
main: com.example.email.EmailPlugin
permissions: [network, storage, secrets]   # mínimo necessário — deny-by-default
dependencies: []              # outros plugin-ids, se houver
```

### 4. Implementar o plugin (só lifecycle + declaração)

```java
public final class EmailPlugin implements AutomationPlugin {
    private PluginContext ctx;

    public PluginMetadata metadata() {
        return new PluginMetadata("email-automation", "Email Automation", "1.0.0", "1.0");
    }

    public List<AutomationDescriptor> automations() {
        return List.of(AutomationDescriptor.of("email.send", "Enviar e-mail",
                Map.of("to", "string", "subject", "string", "body", "string")));
    }

    public void initialize(PluginContext context) { this.ctx = context; }

    public void shutdown() { /* fecha Session/Transport — idempotente */ }
}
```

### 5. Implementar cada `Automation` (a unidade executável)

```java
public final class SendEmail implements Automation {
    public String id() { return "email.send"; }

    public AutomationResult execute(AutomationContext ctx) {
        ctx.log().info("enviando e-mail");
        String pwd = ctx.secrets().get("smtp.password").orElseThrow();  // nunca no plugin.yml
        return AutomationResult.ok(Map.of("messageId", "..."));
    }
}
```

Regra: sem `static` mutável, sem `System.out`, sem `ExecutorService` próprio — tudo vem do contexto.

### 6. Testar com o `PluginTestKit` (sem Core, SQLite em temp)

```java
@Test void enviaComPermissao() {
    try (var core = PluginTestKit.start(new EmailPlugin())) {
        var r = core.run("email.send", Parameters.of("to", "a@b.c"));
        assertThat(r.status()).isEqualTo(Status.SUCCESS);
    }
}

@Test void semPermissaoDeRedeFalha() {
    var core = PluginTestKit.start(new EmailPlugin(), Permissions.none());
    assertPermissionDenied(core, "email.send");
}
```

### 7. `mvn verify` no repositório do plugin

Compila contra a API publicada, roda unit + integração (failsafe), jacoco e o **teste de contrato** herdado do archetype (implementa todos os métodos? metadados válidos? ids únicos?).

### 8. Validar o artefato antes de instalar

```bash
automation-cli plugin validate ./target/email-automation-1.0.0.jar
```

Sete checagens, com mensagem por campo: manifesto contra JSON Schema · `id`/`version` no formato · `api-version` compatível · `main` existe e implementa `AutomationPlugin` · permissões declaradas cobrem o que o plugin usa (`PermissionProbe` observa as chamadas no smoke) · dependências resolvíveis · **smoke run** (`initialize` → `shutdown`) em classloader isolado, sem tocar outros plugins.

### 9. Instalar, habilitar, executar

```bash
automation-cli plugin install ./target/email-automation-1.0.0.jar   # copia p/ plugins/, valida de novo
automation-cli plugin enable email-automation
automation-cli plugins list                                          # estado ENABLED
automation-cli run email.send -p to=dev@example.com -p subject=Teste
automation-cli runs --limit 5                                        # RunRecord + duração
automation-cli logs <runId>                                          # log escopado
```

### 10. Publicar

SemVer + CHANGELOG + SHA-256 do JAR; tag `email-automation-v1.0.0`. Mudança incompatível de contrato = `api-version` maior e janela de deprecação nos exemplos.

### Pipeline de validação (onde cada gate roda)

| Gate | Onde | Falha bloqueia |
|---|---|---|
| `mvn verify` | dev / CI do plugin | merge |
| `plugin validate` | pre-commit + CI | instalação |
| isolamento + permissões | CI do Core (matrix com os 3 exemplos) | release do Core |
| smoke run em canary | CI, antes de promover | deploy |

**Definition of Done de um plugin:** manifesto válido · sem `static` mutável · permissões mínimas declaradas · testes com `PluginTestKit` cobrindo caminho feliz + permissão negada · `plugin validate` verde · `shutdown()` idempotente sem vazar thread · `mvn -q verify` verde.

---

## Tasks

- [x] **T0 — Ambiente + repo** · `devops-engineer`
  `JAVA_HOME` → JDK 21 (usuário), Maven 3.10 no PATH, branch `feature/automation-core`, `.gitignore`.
  → *Verificado:* `mvn -v` = `Java 21.0.12`; `git branch --show-current` = `feature/automation-core`. Docker/Postgres descartados.

- [x] **T1 — Esqueleto multi-módulo** · `backend-specialist` / `clean-code`
  Parent POM + 5 módulos (`api`, `core`, `cli`, `testkit`, `archetype`), `maven-enforcer` (Java 21, Maven 3.9+, duplicatas), compiler `release=21`, surefire/failsafe, jacoco, spotless/google-java-format.
  → *Verificar:* `mvn -q -DskipTests verify` verde; `mvn -q dependency:tree -pl automation-api` só `slf4j-api`.

- [ ] **T2 — Contratos (`automation-api`)** · `backend-specialist` / `api-patterns`
  Interfaces e records da seção "Contratos", `Parameters` tipado, `CancellationToken`, `ProgressSink`, exceções (`PluginLoadException`, `PermissionDeniedException`, `AutomationException`, `CancellationRequested`).
  → *Verificar:* javadoc sem warnings; teste de contrato rejeita `id` duplicado ou metadados nulos.

- [ ] **T3 — Manifesto + validação de schema** · `backend-specialist` / `api-patterns`
  `plugin.yml`, JSON Schema, `PluginDescriptor`, compatibilidade `api-version`, `PluginValidator` (as 7 checagens) reutilizável pelo CLI.
  → *Verificar:* fixtures inválidas (id maiúsculo, `main` ausente, permission desconhecida, `api-version: 2.0`) retornam erro com campo e linha.

- [ ] **T4 — Discovery + loader + máquina de estados** · `backend-specialist` / `clean-code`
  Varredura de `plugins/`, `URLClassLoader` filho com parent no loader da API, registro, transições tabeladas, `close()` obrigatório no unload.
  → *Verificar:* classe interna do plugin A invisível ao B; transição ilegal lança; teste de leak com `WeakReference` + thread sem dono.

- [ ] **T5 — Serviços + permissões + persistência** · `security-auditor` / `vulnerability-scanner`
  `DefaultPluginContext`: `Logger` (MDC `pluginId`/`runId`), `Configuration` tipada, `Storage` (`kv_store`, namespaced), `EventPublisher` (virtual threads), `SecretAccess` (AES-GCM em `secret` + env), `PermissionGuard` deny-by-default, `PermissionProbe`, `SingleWriter` + migração.
  → *Verificar:* plugin sem `network` recebe `PermissionDeniedException`; `Storage` do A não alcança chave do B; segredo nunca em log; `PermissionProbe` acusa permissão usada e não declarada; 200 escritas concorrentes sem `SQLITE_BUSY`.

- [ ] **T6 — Execution Engine** · `backend-specialist` / `testing-patterns`
  `newVirtualThreadPerTaskExecutor`, bulkhead (`Semaphore` por plugin), timeout, cancelamento (flag + interrupt), `RunRecord`, retry com backoff, `outputs` serializado.
  → *Verificar:* `sleep(5s)` com timeout 1s → `TIMED_OUT`; 4 concorrentes respeitam o limite por plugin; `runs` mostra duração.

- [ ] **T7 — Scheduler desacoplado** · `backend-specialist` / `api-patterns`
  `Trigger` sealed (`cron`, `interval`, `event`), `ScheduledThreadPoolExecutor` só agenda → chama Engine; `schedule` + `schedule_fire`; misfire `skip` padrão; `cron-utils`.
  → *Verificar:* cron `*/5 * * * * *` dispara 3x em 16s, drift < 1s; restart não duplica (constraint); nenhuma rajada após downtime.

- [ ] **T8 — Workflow engine (DAG)** · `backend-specialist` + `database-architect` / `database-design`
  `workflow.yml` (`steps[].{id, use, with, needs, onError, timeout}`), template `${inputs.x}`/`${steps.y.outputs.z}`, ordem topológica, nós prontos em paralelo, estado por nó, retomada.
  → *Verificar:* workflow de 4 passos em 3 plugins conclui; matar processo no nó 3 e reiniciar retoma do 3; ciclo no YAML falha na validação.

- [ ] **T9 — Archetype + TestKit** · `test-engineer` / `testing-patterns`
  `automation-plugin-archetype` gerando o esqueleto do passo 2; `PluginTestKit` (`start`, `run`, `assertPermissionDenied`, `Permissions.none()`, SQLite em diretório temporário); teste de contrato herdado.
  → *Verificar:* `mvn archetype:generate` produz projeto que compila e cujo `mvn verify` passa sem edição; TestKit roda em < 20s.

- [ ] **T10 — CLI + observabilidade + CI** · `backend-specialist` + `devops-engineer` / `deployment-procedures`
  `plugins list|info|enable|disable|reload`, `plugin validate|install|uninstall`, `run`, `runs`, `logs`, `workflow run`, `doctor`; logs JSON opcionais; GitHub Actions (build + verify + jacoco + matrix dos exemplos).
  → *Verificar:* `plugin validate` reprova JAR adulterado; `run email.send` retorna exit 0 e `runId`; `doctor` acusa banco inacessível com mensagem clara.

- [ ] **T11 — Exemplos + guia + hardening** · `documentation-writer` + `security-auditor`
  3 plugins de exemplo com testes (GreenMail, POI, Jsoup/MockWebServer), `docs/PLUGIN_GUIDE.md` (o passo a passo acima), ADRs D1–D6, política de deprecação (SemVer + `@Deprecated(forRemoval)`), teste de compatibilidade binária.
  → *Verificar:* cada exemplo compila só contra `automation-api` + `testkit`; guia executado ponta a ponta pelo time sem ajuda; `docs/adr/` com 6 arquivos; cobertura do core ≥ 80%.

## Riscos

| Risco | Impacto | Mitigação |
|-------|---------|-----------|
| SQLite: escritor único vira gargalo sob carga | Médio | `SingleWriter` com fila + WAL; escritas agrupadas por transação; métrica de fila |
| `SQLITE_BUSY` em concorrência | Médio | `busy_timeout=5000`, retry com backoff no `SingleWriter`, nunca transação durante IO externo |
| Banco como arquivo único = backup é copiar arquivo | Alto (perda) | `VACUUM INTO` diário em `data/backups/`; `run`/`run_log` com retenção configurável |
| `URLClassLoader` vaza threads/recursos no unload | Alto | `close()` obrigatório + teste de leak; `PluginHost` mantém a costura para processo separado |
| Plugin interno mal comportado compromete o Core | Médio | Permissões na fachada + revisão; **não** prometer sandbox (ADR D2) |
| Conflito de dependências (POI, Mail) entre plugins | Médio | classloader filho por plugin; API compartilhada |
| Escopo inflar para REST/UI antes do Core | Alto | D5 explícita; T10 é a última entrega funcional |

## Decisões em aberto

1. **REST/API HTTP**: só em v2, quando houver consumidor real?
2. **Secrets**: AES-GCM no SQLite ou já existe cofre (Vault/AWS Secrets Manager) no time?
3. **Retenção** de `run`/`run_log`: quantos dias antes de purgar?

## Phase X — Verificação final

```bash
./mvnw -q clean verify                                 # build + unit + integração + jacoco
./mvnw -q -pl examples/email-plugin verify             # plugin de exemplo contra a API
java -jar automation-cli/target/automation-cli.jar doctor
java -jar automation-cli/target/automation-cli.jar plugin validate examples/email-plugin/target/email-automation-1.0.0.jar
java -jar automation-cli/target/automation-cli.jar plugins list
java -jar automation-cli/target/automation-cli.jar run scraper.fetch -p url=https://example.com
java -jar automation-cli/target/automation-cli.jar runs --limit 5
./mvnw -q -pl automation-core test -Dtest=PermissionEnforcementTest,PluginIsolationTest,WorkflowResumeTest,SqliteConcurrencyTest
```

Checklist: plugins isolados ✅ · permissões negadas por padrão ✅ · falha de plugin não derruba o Core ✅ · run history consultável ✅ · workflow retomável ✅ · escrita concorrente sem `SQLITE_BUSY` ✅ · cobertura do core ≥ 80% ✅ · guia de plugin executável ✅ · ADRs escritas ✅ · nenhuma credencial em `plugin.yml` ou log ✅

> Nota: `python .agents/scripts/checklist.py .` é orientado a web (Lighthouse, Playwright) — usar apenas `security_scan.py` como varredura de segredos.
