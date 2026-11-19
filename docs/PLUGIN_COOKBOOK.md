# Receitas de plugin

Soluções curtas para o que aparece no dia a dia. Cada receita é um recorte de código real do SIA.

## 1. Chamar um executável externo (sem `ProcessBuilder`)

O plugin **não** recebe `ProcessBuilder`. Ele pede por nome lógico, e o administrador define o comando
no `config.yml`.

```yaml
# config/local.yml
processes:
  meu-bot:
    command: ["${java.home}/bin/java", "-jar", "C:/bots/meu-bot.jar"]
    timeout-minutes: 10
```

```java
ProcessResult result = context.processes().run(
    ProcessRequest.of("meu-bot", "--entrada", arquivo).withTimeout(Duration.ofMinutes(5)));

if (result.exitCode() != 0) {
  return AutomationResult.failure("BOT_FALHOU", result.stderrTail(400));
}
```

No `plugin.yml`: `permissions: [process]`.

## 2. Reportar progresso (aparece na interface e no SSE)

```java
context.progress().step("lendo arquivos");     // mensagem sem percentual
context.progress().progress(40, "4 de 10");    // percentual + mensagem
```

## 3. Respeitar cancelamento

```java
for (Linha linha : linhas) {
  if (context.cancellation().isCancelled()) {
    return AutomationResult.failure("CANCELADO", "cancelado pelo usuario");
  }
  processar(linha);
}
```

Em laço longo, verifique a cada iteração ou a cada bloco de trabalho.

## 4. Guardar estado entre execuções

```java
String ultimo = context.storage().get("ultimo-pedido").orElse("0");
context.storage().put("ultimo-pedido", pedido);
```

`storage` é chave-valor por plugin (namespace próprio), exige `permissions: [storage]`.

## 5. Ler segredo sem gravar em log

```java
String senha = context.secrets().require("SMTP_SENHA");   // permissions: [secrets]
context.log().info("autenticando no SMTP");               // sem concatenar a senha
```

O Core resolve `SIA_SECRET_<PLUGIN>_<NOME>` do ambiente e redige valores sensíveis no log. **Nunca**
interpole o segredo em mensagem.

## 6. Ler um arquivo de entrada com segurança

```java
Path base = context.config().path("pasta-entrada").orElse(Path.of("."));
Path arquivo = base.resolve(nomeDoArquivo).normalize();
if (!arquivo.startsWith(base.normalize())) {
  return AutomationResult.failure("CAMINHO_INVALIDO", "arquivo fora da pasta permitida");
}
```

## 7. Configuração do plugin

```yaml
# config.yml do plugin (ao lado do plugin.yml, opcional)
pasta-entrada: C:/entrada
limite-diario: 200
```

```java
int limite = context.config().integer("limite-diario", 100);
```

## 8. Duas automações que compartilham código

```java
public List<Automation> automations() {
  return List.of(new Baixar(this.context), new Enviar(this.context));
}
```

Guarde o `PluginContext` no `initialize` e passe adiante. Nunca use `static` mutável — o mesmo plugin
pode rodar duas execuções ao mesmo tempo.

## 9. Painel próprio (interface do plugin)

Ver [PLUGIN_UI.md](PLUGIN_UI.md). Resumo:

```yaml
ui:
  entry: ui/panel.html
  width: 560
  height: 460
```

```js
parent.postMessage({ type: "sia:execute", automation: "meu-plugin.rodar", params: { x: 1 } }, "*");
addEventListener("message", (e) => { if (e.data.type === "sia:result") mostrar(e.data.outputs); });
```

## 10. Testar sem subir o daemon

```java
try (PluginTestKit kit = PluginTestKit.start(new MeuPlugin(), Permissions.all())) {
  kit.run("meu-plugin.rodar", Map.of("entrada", "x"))
     .assertSuccess()
     .assertNoSensitiveData("senha");
}
```

| Método | Uso |
|---|---|
| `Permissions.none()` | provar que o plugin falha sem a permissão |
| `Permissions.of(STORAGE)` | testar permissão isolada |
| `assertFailure("CODIGO")` | caminho de erro |
| `assertNoSensitiveData(valor)` | garantir que não vazou no log |

## 11. Mensagens de erro que o usuário entende

```java
return AutomationResult.failure(
    "BOT_BANCO_OCUPADO",
    "o programa do Receitanet está aberto; feche e execute de novo");
```

A interface traduz `errorCode` conhecido para frase com próxima ação e mostra `message` como detalhe.
Código curto em maiúsculas, mensagem em português, sem stack trace.

## 12. Instalar e atualizar

```bash
# 1. validar (não executa nada)
curl -X POST localhost:8080/api/plugins/validate -H "Content-Type: application/json" \
  -d '{"path":"C:/build/meu-plugin-1.1.0.jar"}'

# 2. instalar (id já instalado é recusado)
curl -X POST localhost:8080/api/plugins/install -H "Content-Type: application/json" \
  -d '{"path":"C:/build/meu-plugin-1.1.0.jar"}'

# 3. atualizar um plugin existente
curl -X POST localhost:8080/api/plugins/meu-plugin/uninstall
# ... instalar de novo com a nova versão
```

Antes de atualizar: se a automação mudou de parâmetro, suba a versão **major** — o histórico antigo
não vai casar com o novo formato.
