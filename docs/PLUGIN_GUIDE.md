# Guia do autor de plugin — SIA

Este guia é o caminho completo: gerar, implementar, testar, validar e operar um plugin no
**Sistema Integrado de Automações**. O exemplo real usado como referência é o
[`receitanet-bx`](../integrations/receitanet-bx), que roda em produção.

---

## 1. Antes de escrever código: escolha o modo

| | `kind: java` | `kind: process` |
|---|---|---|
| Depende de `sia-api`? | sim | não — só do protocolo JSON Lines |
| Linguagem | Java 23+ | qualquer (Python, Node, Go, shell) |
| Isolamento de falha | bulkhead (divide a JVM do daemon) | total: crash fica no filho |
| Latência por execução | ~0 | start do processo |
| Paralelismo | até 4 runs simultâneos | **1 run por vez** (protocolo v1) |
| Melhor para | automação de negócio em Java | wrapper de ferramenta externa, script, protótipo |

Regra prática: **a automação já é um CLI?** → `process`. **Vai usar `Storage`, `Secrets` e
`ProcessRunner` com frequência?** → `java`. Um adaptador `java` sobre uma CLI é o que o Receitanet
usa (ADR-0001).

---

## 2. Gerar o esqueleto

```bash
mvn archetype:generate -DinteractiveMode=false \
  -DarchetypeGroupId=com.accord \
  -DarchetypeArtifactId=sia-plugin-archetype \
  -DarchetypeVersion=1.0.0-SNAPSHOT \
  -DgroupId=com.accord.plugins \
  -DartifactId=meu-plugin \
  -Dversion=1.0.0 \
  -DpluginId=meu-plugin \
  -Dpackage=com.accord.plugins.meu
```

Saída:

```
meu-plugin/
├── pom.xml
├── plugin.yml
└── src/main/java/com/accord/plugins/meu/
    ├── SamplePlugin.java
    └── SampleAutomation.java
    └── src/test/java/.../SamplePluginTest.java
```

O projeto já compila e o teste passa **sem edição**: `cd meu-plugin && mvn test`.

---

## 3. O manifesto (`plugin.yml`)

```yaml
id: meu-plugin                # ^[a-z][a-z0-9-]{2,63}$ — minúsculo, com hífen
name: Meu Plugin
version: 1.0.0                # SemVer do plugina
api-version: "1.0"            # major precisa casar com o Core
author: Time de Automação
description: Uma frase sobre o que o plugin faz
kind: java                    # java | process
main: com.accord.plugins.meu.SamplePlugin   # só no kind=java
ui:                           # OBRIGATÓRIO: a tela do plugin
  entry: ui/panel.html
  width: 560
  height: 460
permissions: [storage]        # mínimo necessário — deny-by-default
```

No modo `process` troque `main` por:

```yaml
kind: process
command: ["python", "main.py"]   # caminho relativo ao pacote, ou nome no PATH
ui:
  entry: ui/panel.html
```

O validador recusa: campo desconhecido, id fora do formato, versão fora de SemVer, `api-version`
incompatível, `kind` ausente, `main` faltando (java) ou `command` faltando (process), caminho
absoluto, `..`, protocolo diferente de `stdio-jsonl-v1`, permissão desconhecida, dependência
inexistente, `requires-os` inválido e **`ui` ausente**.

---

## 4. O contrato

```java
public final class MeuPlugin implements AutomationPlugin {
  public PluginMetadata metadata() { ... }              // estável durante toda a vida
  public List<Automation> automations() { ... }         // declarativo, sem IO
  public void initialize(PluginContext ctx) { ... }     // 1x, após carga
  public void shutdown() { ... }                        // idempotente
}

public final class MinhaAutomacao implements Automation {
  public AutomationDescriptor describe() { ... }        // nome, params, persistOutputs
  public AutomationResult execute(AutomationContext ctx) { ... }
}
```

`describe()` e `execute()` ficam no **mesmo objeto** de propósito: o catálogo não pode divergir do
código.

### Parâmetros e dado sensível

```java
ParamSpec.optional("smtpSenha", ParamType.STRING, "senha do SMTP").asSensitive()
```

`sensitive` é o que faz o Core redigir o valor em log, mensagem de erro e saída de processo — e
proibir `defaultValue`. Marque **sempre** que o valor for senha, token, PIN, CPF ou dado pessoal.

### Saída

```java
return AutomationResult.success(Map.of("protocolo", protocolo), "enviado");
```

O histórico guarda apenas as **chaves** de saída. Para encadear passos de workflow, os valores
existem em memória durante a execução.

---

## 5. Serviços disponíveis

Tudo vem do contexto — o plugin nunca recebe o Core:

| Serviço | Permissão | Uso |
|---|---|---|
| `ctx.log()` | — | log estruturado; **sem interpolação de valores** |
| `ctx.config()` | — | configuração do plugin (`config.yml`) |
| `ctx.storage()` | `storage` | chave-valor namespaced por plugin |
| `ctx.secrets()` | `secrets` | segredo resolvido por nome, escopado ao plugin |
| `ctx.events()` | — | eventos para outros plugins (não persistidos) |
| `ctx.processes()` | `process` | executar processo pela allowlist do Core |
| `ctx.progress()` | — | progresso (vira SSE no daemon) |
| `ctx.cancellation()` | — | cancelamento cooperativo |

**Proibido no plugin:** `static` mutável, `System.out`, `ExecutorService` próprio,
`ProcessBuilder` direto e credencial literal no código.

---

## 6. Testar com o `PluginTestKit`

```java
try (PluginTestKit kit = PluginTestKit.start(new MeuPlugin(), Permissions.all())) {
  kit.run("meu-plugin.enviar", Map.of("destino", "x"))
     .assertSuccess()
     .assertNoSensitiveData("senha");
}
```

| Método | Para que serve |
|---|---|
| `Permissions.none()` | prova que o plugin só funciona com a permissão declarada |
| `Permissions.of(STORAGE)` | testa cada permissão isoladamente |
| `result.assertSuccess()` | falha com status, `errorCode` e mensagem |
| `result.assertFailure("CODE")` | caminho de erro esperado |
| `result.assertNoSensitiveData(valor)` | garante que o valor não vazou para o log |

O kit sobe SQLite temporário, engine e contexto — sem daemon, sem HTTP, sem tocar `data/sia.db`.

---

## 7. Validar e instalar

O JAR precisa conter o `plugin.yml` (o archetype já coloca em `src/main/resources`).

```bash
# 1. valida sem instalar
curl -s -X POST localhost:8080/api/plugins/validate \
  -H "Authorization: Bearer $SIA_TOKEN" -H "Content-Type: application/json" \
  -d '{"path":"/caminho/meu-plugin-1.0.0.jar"}'
# {"id":"meu-plugin","version":"1.0.0","kind":"java","mainClassPresent":true}

# 2. instala (copia, extrai manifesto, carrega e habilita)
curl -s -X POST localhost:8080/api/plugins/install \
  -H "Authorization: Bearer $SIA_TOKEN" -H "Content-Type: application/json" \
  -d '{"path":"/caminho/meu-plugin-1.0.0.jar"}'

# 3. executa
java -jar sia-cli/target/sia-cli-1.0.0-SNAPSHOT-all.jar --token $SIA_TOKEN run meu-plugin.enviar -p destino=x
```

### Operação do dia a dia

```bash
sia-cli plugins                      # estado e automações por plugin
sia-cli automations                  # catálogo com parâmetros
sia-cli runs --limit 20              # histórico (ator, trigger, status)
sia-cli logs <runId>                 # log da execução
sia-cli cancel <runId>               # cancelamento cooperativo
```

Agendar:

```bash
curl -X POST localhost:8080/api/schedules -H "Authorization: Bearer $SIA_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"automation":"meu-plugin.enviar","trigger":{"type":"cron","expression":"0 0 3 * * *"},"params":{}}'
```

Encadear em workflow (`workflows/meu-processo.yml`):

```yaml
name: meu-processo
steps:
  - id: baixar
    use: receitanet.baixar
  - id: avisar
    use: meu-plugin.enviar
    needs: [baixar]
    with:
      destino: "${steps.baixar.outputs.arquivo}"
```

---

## 8. Problemas comuns

| Sintoma | Causa provável | Solução |
|---|---|---|
| `PERMISSION_DENIED` | serviço usado sem permissão no manifesto | declarar em `permissions` e republicar |
| `metadata.apiVersion incompativel` | plugin compilado contra outra major | recompilar contra a API 1.x atual |
| `handshake falhou` (process) | plugin não respondeu `describe` em 15 s | conferir se o processo lê `stdin` e escreve JSON por linha |
| `BOT_BANCO_OCUPADO` (Receitanet) | programa oficial aberto usando o mesmo Derby | fechar o oficial antes de rodar |
| `PLUGIN_TIMEOUT` | plugin passou do tempo do passo | reduzir trabalho ou aumentar timeout na automação |
| `ClassNotFoundException` no plugin | classe faltando no JAR (fora do shade) | empacotar dependências no artefato |

---

## 9. Definition of Done de um plugin

- [ ] manifesto válido (`plugin validate` verde)
- [ ] sem `static` mutável, `System.out` ou `ProcessBuilder`
- [ ] permissões mínimas declaradas
- [ ] `sensitive` marcado em senha, token, PIN e dado pessoal
- [ ] testes com `PluginTestKit`: caminho feliz **e** permissão negada
- [ ] `assertNoSensitiveData` no teste com dado sensível
- [ ] `shutdown()` idempotente, sem thread ou conexão vazando
- [ ] `mvn verify` do plugin verde
- [ ] se tiver painel: `ui.entry` no manifesto e página testada em 520×460

---

## 10. Interface própria do plugin (obrigatória)

Todo plugin entrega a **própria tela** — escolher arquivo, mostrar prévia, acompanhar etapas. É
obrigatório declarar no manifesto:

```yaml
ui:
  entry: ui/panel.html   # dentro do pacote
  width: 520
  height: 460
```

O console abre essa página num painel isolado e conversa por mensagens:

```js
parent.postMessage({ type: "sia:execute", automation: "meu-plugin.rodar", params: { x: 1 } }, "*");
addEventListener("message", (e) => { if (e.data.type === "sia:result") mostrar(e.data.outputs); });
```

O resultado chega **com valores** (execução `wait=true`), mas o histórico continua guardando apenas as
chaves. Guia completo do painel e do protocolo: [PLUGIN_UI.md](PLUGIN_UI.md).

## 11. Versionamento

- **Versão do plugin** (`version`): SemVer. `fix` → patch, automação nova → minor, mudança
  incompatível em parâmetro ou saída → major.
- **Versão da API** (`api-version`): o Core rejeita major diferente. Deprecação de método público usa
  `@Deprecated(forRemoval = true)` com uma minor de aviso antes da remoção.
- Compatibilidade binária é verificada compilando os plugins de exemplo contra a API publicada.
