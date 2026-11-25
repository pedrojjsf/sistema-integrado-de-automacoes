package com.accord.sia.cli;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/** Cliente de linha de comando do daemon SIA. */
@Command(
    name = "sia-cli",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    description = "Cliente do Sistema Integrado de Automacoes",
    subcommands = {
      SiaCli.Doctor.class,
      SiaCli.Plugins.class,
      SiaCli.Automations.class,
      SiaCli.Run.class,
      SiaCli.Runs.class,
      SiaCli.Logs.class,
      SiaCli.Cancel.class
    })
public final class SiaCli implements Runnable {

  @Mixin Target target;

  @Override
  public void run() {
    CommandLine.usage(this, System.out);
  }

  public static void main(String[] args) {
    int exit = new CommandLine(new SiaCli()).execute(args);
    System.exit(exit);
  }

  /** Opcoes comuns a todos os comandos. */
  static final class Target {
    @Option(names = "--url", defaultValue = "http://127.0.0.1:8080", description = "URL do daemon")
    String url;

    @Option(
        names = "--token",
        defaultValue = "${env:SIA_TOKEN}",
        description = "Bearer token (ou variavel SIA_TOKEN)")
    String token;

    ApiClient client() {
      return new ApiClient(url, token);
    }
  }

  abstract static class Base implements Callable<Integer> {
    @ParentCommand SiaCli root;

    @Override
    public final Integer call() {
      try {
        return execute(root.target.client());
      } catch (ApiClient.CliException error) {
        System.err.println("erro: " + error.getMessage());
        return 1;
      }
    }

    abstract int execute(ApiClient client);
  }

  @Command(name = "doctor", description = "Verifica se o daemon responde e mostra o resumo")
  static final class Doctor extends Base {
    @Override
    int execute(ApiClient client) {
      JsonNode health = client.get("/api/health");
      System.out.println("status   : " + health.path("status").asText());
      System.out.println("versao   : " + health.path("version").asText());
      System.out.println("auth     : " + health.path("auth").asText());
      System.out.println("plugins  : " + health.path("plugins").asInt());
      System.out.println("executando: " + health.path("running").asInt());
      JsonNode failures = health.path("failures");
      if (failures.isArray() && !failures.isEmpty()) {
        System.out.println("falhas   :");
        failures.forEach(failure -> System.out.println("  - " + failure.asText()));
      }
      return 0;
    }
  }

  @Command(name = "plugins", description = "Lista os plugins carregados")
  static final class Plugins extends Base {
    @Override
    int execute(ApiClient client) {
      JsonNode plugins = client.get("/api/plugins");
      if (plugins.isEmpty()) {
        System.out.println("nenhum plugin carregado");
        return 0;
      }
      System.out.printf("%-20s %-8s %-10s %s%n", "ID", "VERSAO", "ESTADO", "AUTOMACOES");
      plugins.forEach(plugin ->
          System.out.printf(
              "%-20s %-8s %-10s %s%n",
              plugin.path("id").asText(),
              plugin.path("version").asText(),
              plugin.path("state").asText(),
              plugin.path("automations").size()));
      return 0;
    }
  }

  @Command(name = "automations", description = "Lista as automacoes disponiveis")
  static final class Automations extends Base {
    @Override
    int execute(ApiClient client) {
      JsonNode automations = client.get("/api/automations");
      automations.forEach(automation -> {
        System.out.println(
            automation.path("id").asText() + " - " + automation.path("name").asText());
        JsonNode params = automation.path("params");
        if (!params.isEmpty()) {
          params.forEach(param ->
              System.out.printf(
                  "    %s (%s)%s%s%n",
                  param.path("name").asText(),
                  param.path("type").asText(),
                  param.path("required").asBoolean() ? " obrigatorio" : "",
                  param.path("sensitive").asBoolean() ? " [sensivel]" : ""));
        }
      });
      return 0;
    }
  }

  @Command(name = "run", description = "Executa uma automacao")
  static final class Run extends Base {
    @Parameters(index = "0", paramLabel = "AUTOMACAO", description = "ex.: hello.say")
    String automation;

    @Option(
        names = {"-p", "--param"},
        description = "parametro no formato chave=valor")
    Map<String, String> params = new LinkedHashMap<>();

    @Option(names = "--no-wait", description = "retorna o runId sem esperar o termino")
    boolean noWait;

    @Option(names = "--timeout", defaultValue = "300", description = "espera maxima em segundos")
    int timeoutSeconds;

    @Override
    int execute(ApiClient client) {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("automation", automation);
      body.put("params", new LinkedHashMap<>(params));
      JsonNode created = client.post("/api/runs", body);
      String runId = created.path("runId").asText();
      System.out.println("run " + runId);
      if (noWait) {
        return 0;
      }
      JsonNode run = await(client, runId);
      System.out.println(
          "status " + run.path("status").asText() + " (" + run.path("finishedAt").asText() + ")");
      if (!run.path("errorCode").isNull()) {
        System.out.println(
            "erro   " + run.path("errorCode").asText() + ": " + run.path("message").asText());
      }
      JsonNode outputs = run.path("outputs");
      if (!outputs.isEmpty()) {
        System.out.println("saidas " + outputs);
      }
      return "SUCCESS".equals(run.path("status").asText()) ? 0 : 2;
    }

    private JsonNode await(ApiClient client, String runId) {
      long deadline = System.nanoTime() + timeoutSeconds * 1_000_000_000L;
      JsonNode run = client.get("/api/runs/" + runId);
      while ("RUNNING".equals(run.path("status").asText()) && System.nanoTime() < deadline) {
        sleep();
        run = client.get("/api/runs/" + runId);
      }
      return run;
    }

    private static void sleep() {
      try {
        Thread.sleep(300);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Command(name = "runs", description = "Lista execucoes recentes")
  static final class Runs extends Base {
    @Option(names = "--limit", defaultValue = "20")
    int limit;

    @Override
    int execute(ApiClient client) {
      JsonNode runs = client.get("/api/runs?limit=" + limit);
      System.out.printf("%-38s %-16s %-8s %s%n", "RUN", "AUTOMACAO", "STATUS", "ATOR");
      runs.forEach(run ->
          System.out.printf(
              "%-38s %-16s %-8s %s%n",
              run.path("id").asText(),
              run.path("automation").asText(),
              run.path("status").asText(),
              run.path("actor").asText()));
      return 0;
    }
  }

  @Command(name = "logs", description = "Mostra o log de uma execucao")
  static final class Logs extends Base {
    @Parameters(index = "0", paramLabel = "RUN", description = "id da execucao")
    String runId;

    @Override
    int execute(ApiClient client) {
      JsonNode logs = client.get("/api/runs/" + runId + "/logs");
      logs.forEach(line -> System.out.println(line.asText()));
      return 0;
    }
  }

  @Command(name = "cancel", description = "Pede cancelamento de uma execucao")
  static final class Cancel extends Base {
    @Parameters(index = "0", paramLabel = "RUN", description = "id da execucao")
    String runId;

    @Override
    int execute(ApiClient client) {
      JsonNode result = client.post("/api/runs/" + runId + "/cancel", Map.of());
      System.out.println("cancelado: " + result.path("cancelled").asBoolean());
      return 0;
    }
  }
}
