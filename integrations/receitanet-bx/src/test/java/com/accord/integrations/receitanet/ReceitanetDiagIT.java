package com.accord.integrations.receitanet;

import static org.assertj.core.api.Assertions.assertThat;

import com.accord.sia.api.Actor;
import com.accord.sia.api.Parameters;
import com.accord.sia.api.PluginContext;
import com.accord.sia.core.context.PluginContextFactory;
import com.accord.sia.core.event.InProcessEventBus;
import com.accord.sia.core.manifest.ManifestLoader;
import com.accord.sia.core.manifest.PluginManifest;
import com.accord.sia.core.persistence.Database;
import com.accord.sia.core.persistence.SchemaMigrator;
import com.accord.sia.core.persistence.SingleWriter;
import com.accord.sia.core.plugin.PluginRuntime;
import com.accord.sia.core.plugin.PluginState;
import com.accord.sia.core.process.ProcessAllowlist;
import com.accord.sia.core.run.ExecutionEngine;
import com.accord.sia.core.run.RunOutcome;
import com.accord.sia.core.run.RunRepository;
import com.accord.sia.core.run.RunRequest;
import com.accord.sia.core.run.RunStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Executa o bot real do Receitanet atraves do SIA.
 *
 * <p>Roda com {@code mvn verify}. Se o bot nao estiver na maquina, o teste e ignorado em vez de
 * falhar — CI nao tem o binario.
 */
class ReceitanetDiagIT {

  @Test
  void executaDiagnosticoDoBotReal() throws Exception {
    Path botJar = botJar();
    Assumptions.assumeTrue(
        botJar != null && Files.isRegularFile(botJar),
        "bot do Receitanet nao encontrado; defina -Dsia.receitanet.jar=<caminho>");

    Path work = Files.createTempDirectory("sia-receitanet-it");
    Database database = Database.open(work.resolve("sia.db"));
    new SchemaMigrator().migrate(database);

    try (SingleWriter writer = new SingleWriter();
        ExecutionEngine engine =
            new ExecutionEngine(
                new RunRepository(database, writer, Clock.systemUTC()),
                Clock.systemUTC(),
                1,
                Duration.ofSeconds(10),
                Duration.ofMinutes(3))) {

      ProcessAllowlist allowlist =
          ProcessAllowlist.ofSpecs(
              Map.of(
                  BotAutomation.EXECUTABLE,
                  new ProcessAllowlist.Spec(command(botJar), Duration.ofMinutes(2))));
      PluginContextFactory contexts =
          new PluginContextFactory(
              database, writer, allowlist, new InProcessEventBus(), Clock.systemUTC());

      PluginManifest manifest = new ManifestLoader().read(manifestPath());
      PluginContext context = contexts.forPlugin(manifest, Map.of());

      ReceitanetPlugin plugin = new ReceitanetPlugin();
      plugin.initialize(context);

      RunOutcome outcome =
          engine.execute(
              runtimeOf(plugin, context),
              RunRequest.manual(
                      "receitanet.diag", Parameters.empty(), Actor.user("sub-teste", "Teste"))
                  .withTimeout(Duration.ofMinutes(2)));

      assertThat(outcome.status())
          .as("saida do bot: %s", outcome.message())
          .isEqualTo(RunStatus.SUCCESS);
      assertThat(outcome.message()).isEqualTo("diagnostico executado");

      System.out.printf(
          "[SIA] run=%s status=%s duracao=%sms outputs=%s%n",
          outcome.runId(), outcome.status(), outcome.duration().toMillis(), outcome.outputs());
    }
  }

  private static PluginRuntime runtimeOf(ReceitanetPlugin plugin, PluginContext context) {
    return new PluginRuntime() {
      @Override
      public String id() {
        return plugin.metadata().id();
      }

      @Override
      public PluginState state() {
        return PluginState.ENABLED;
      }

      @Override
      public com.accord.sia.api.AutomationPlugin instance() {
        return plugin;
      }

      @Override
      public PluginContext context() {
        return context;
      }
    };
  }

  private static Path manifestPath() {
    return Path.of("src", "main", "resources", "plugin.yml");
  }

  /**
   * Comando base do bot. As flags vem do manifesto do proprio JAR: sem elas o certificado do
   * Windows nao abre.
   */
  private static List<String> command(Path botJar) {
    String java =
        Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                    ? "java.exe"
                    : "java")
            .toString();
    return List.of(
        java,
        "--add-opens=java.base/java.security=ALL-UNNAMED",
        "--add-exports=jdk.crypto.mscapi/sun.security.mscapi=ALL-UNNAMED",
        "-Djava.awt.headless=true",
        "-Dfile.encoding=UTF-8",
        "-cp",
        botJar.toAbsolutePath().toString(),
        "numberia.bot.Inicio");
  }

  private static Path botJar() throws IOException {
    String configured = System.getProperty("sia.receitanet.jar");
    if (configured != null && !configured.isBlank()) {
      return Path.of(configured);
    }
    Path defaultPath =
        Path.of("..", "..", "automacao", "ReceitanetBX-Bot", "app", "receitanetbx-bot.jar");
    return Files.isRegularFile(defaultPath) ? defaultPath.toAbsolutePath() : null;
  }
}
