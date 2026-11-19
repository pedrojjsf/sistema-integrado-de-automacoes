package com.accord.sia.core.process;

import static org.assertj.core.api.Assertions.assertThat;

import com.accord.sia.api.Actor;
import com.accord.sia.api.Parameters;
import com.accord.sia.core.manifest.PluginKind;
import com.accord.sia.core.persistence.Database;
import com.accord.sia.core.persistence.SchemaMigrator;
import com.accord.sia.core.persistence.SingleWriter;
import com.accord.sia.core.plugin.LoadedPlugin;
import com.accord.sia.core.plugin.PluginCandidate;
import com.accord.sia.core.plugin.PluginLoader;
import com.accord.sia.core.run.ExecutionEngine;
import com.accord.sia.core.run.RunOutcome;
import com.accord.sia.core.run.RunRepository;
import com.accord.sia.core.run.RunRequest;
import com.accord.sia.core.run.RunStatus;
import com.accord.sia.core.testsupport.TestContexts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ProcessPluginTest {

  /**
   * Diretorio fora do {@code @TempDir}: o processo filho usa o pacote como working directory e no
   * Windows uma pasta que e cwd de processo vivo nao pode ser apagada.
   */
  private final Path temp = Path.of("target", "process-plugin-test");

  @Test
  void carregaExecutaEDesligaPluginDeProcesso() throws Exception {
    Path directory = pluginDirectory();
    Database database = Database.open(temp.resolve("sia.db"));
    new SchemaMigrator().migrate(database);
    try (SingleWriter writer = new SingleWriter();
        ExecutionEngine engine =
            new ExecutionEngine(
                new RunRepository(database, writer, Clock.systemUTC()),
                Clock.systemUTC(),
                1,
                Duration.ofSeconds(5),
                Duration.ofMinutes(2))) {

      LoadedPlugin plugin = new PluginLoader().load(candidate(directory));
      try {
        assertThat(plugin.manifest().kind()).isEqualTo(PluginKind.PROCESS);
        assertThat(plugin.instance().metadata().id()).isEqualTo("process-plugin");
        assertThat(plugin.instance().automations()).hasSize(1);

        plugin.initialize(TestContexts.noop("process-plugin"));
        plugin.enable();

        RunOutcome outcome =
            engine.execute(
                plugin,
                RunRequest.manual(
                    "process.eco", Parameters.of("texto", "ola"), Actor.user("sub-1", "Teste")));

        assertThat(outcome.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(outcome.outputs()).containsEntry("eco", "ola");
        assertThat(new RunRepository(database, writer, Clock.systemUTC()).logs(outcome.runId()))
            .anyMatch(line -> line.contains("executando eco"));
      } finally {
        plugin.unload();
      }
    }
  }

  private Path pluginDirectory() throws IOException {
    Path directory = Files.createDirectories(temp.resolve("plugins").resolve("process-plugin"));
    String testClasses =
        Path.of("target", "test-classes").toAbsolutePath().toString().replace("\\", "/");
    Files.writeString(
        directory.resolve("plugin.yml"),
        """
        id: process-plugin
        name: Process
        version: 1.0.0
        api-version: "1.0"
        kind: process
        command: ["java", "-cp", "%s", "com.accord.sia.core.process.JsonlTestPlugin"]
        ui:
          entry: ui/panel.html
        """
            .formatted(testClasses));
    Files.write(directory.resolve("plugin.jar"), new byte[] {0});
    java.nio.file.Files.createDirectories(directory.resolve("ui"));
    java.nio.file.Files.writeString(
        directory.resolve("ui").resolve("panel.html"),
        "<!doctype html><html><body>painel do processo</body></html>");
    return directory;
  }

  private static PluginCandidate candidate(Path directory) {
    return new PluginCandidate(
        directory, directory.resolve("plugin.yml"), directory.resolve("plugin.jar"));
  }
}
