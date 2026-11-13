package com.accord.sia.core.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.accord.sia.api.Actor;
import com.accord.sia.api.Automation;
import com.accord.sia.api.AutomationContext;
import com.accord.sia.api.AutomationDescriptor;
import com.accord.sia.api.AutomationPlugin;
import com.accord.sia.api.AutomationResult;
import com.accord.sia.api.ParamSpec;
import com.accord.sia.api.ParamType;
import com.accord.sia.api.Parameters;
import com.accord.sia.api.PluginContext;
import com.accord.sia.api.PluginMetadata;
import com.accord.sia.core.persistence.Database;
import com.accord.sia.core.persistence.SchemaMigrator;
import com.accord.sia.core.persistence.SingleWriter;
import com.accord.sia.core.plugin.PluginRuntime;
import com.accord.sia.core.plugin.PluginState;
import com.accord.sia.core.testsupport.TestContexts;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Prova a regra central do SIA: nada sensivel chega ao banco nem ao historico.
 *
 * <p>O plugin de teste e deliberadamente descuidado — ele concatena o CPF na mensagem de log e
 * devolve o valor do protocolo como saida. Mesmo assim, o banco tem de ficar limpo.
 */
class NoSensitiveDataTest {

  private static final String CPF = "123.456.789-09";
  private static final String PROTOCOLO = "PROTO-SECRETO-9f2c";

  @TempDir Path temp;

  @Test
  void pluginDescuidadoNaoVazaDadoSensivel() throws IOException {
    Database database = Database.open(temp.resolve("sia.db"));
    new SchemaMigrator().migrate(database);
    try (SingleWriter writer = new SingleWriter()) {
      RunRepository repository = new RunRepository(database, writer, Clock.systemUTC());
      try (ExecutionEngine engine =
          new ExecutionEngine(
              repository, Clock.systemUTC(), 2, Duration.ofSeconds(1), Duration.ofSeconds(5))) {

        RunOutcome outcome =
            engine.execute(
                carelessPlugin(),
                RunRequest.manual(
                    "fake.envio",
                    Parameters.of("cpf", CPF),
                    Actor.user("sub-analista", "Analista")));

        assertThat(outcome.succeeded()).isTrue();

        RunRecord record = repository.find(outcome.runId()).orElseThrow();
        assertThat(record.paramsKeys()).containsExactly("cpf");
        assertThat(record.outputsKeys()).containsExactly("protocolo");

        List<String> logs = repository.logs(outcome.runId());
        assertThat(logs).anyMatch(line -> line.contains(Redactor.MASK));
        assertThat(logs).noneMatch(line -> line.contains(CPF));

        String databaseContent = readAllDatabaseFiles(temp);
        assertThat(databaseContent).doesNotContain(CPF).doesNotContain(PROTOCOLO);
      }
    }
  }

  private static String readAllDatabaseFiles(Path directory) throws IOException {
    StringBuilder content = new StringBuilder();
    try (Stream<Path> files = Files.list(directory)) {
      for (Path file : files.filter(Files::isRegularFile).toList()) {
        content.append(new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1));
      }
    }
    return content.toString();
  }

  private static PluginRuntime carelessPlugin() {
    Automation automation =
        new Automation() {
          @Override
          public AutomationDescriptor describe() {
            return AutomationDescriptor.of(
                "fake.envio",
                "Envio",
                "automacao descuidada de teste",
                List.of(
                    ParamSpec.optional("cpf", ParamType.STRING, "CPF do contribuinte")
                        .asSensitive()));
          }

          @Override
          public AutomationResult execute(AutomationContext context) {
            String cpf = context.params().string("cpf").orElse("");
            context.log().info("processando CPF " + cpf);
            return AutomationResult.success(Map.of("protocolo", PROTOCOLO), "enviado para " + cpf);
          }
        };
    AutomationPlugin plugin =
        new AutomationPlugin() {
          @Override
          public PluginMetadata metadata() {
            return new PluginMetadata("fake-plugin", "Fake", "1.0.0", "1.0");
          }

          @Override
          public List<Automation> automations() {
            return List.of(automation);
          }

          @Override
          public void initialize(PluginContext context) {}

          @Override
          public void shutdown() {}
        };
    PluginContext context = TestContexts.noop("fake-plugin");
    return new PluginRuntime() {
      @Override
      public String id() {
        return "fake-plugin";
      }

      @Override
      public PluginState state() {
        return PluginState.ENABLED;
      }

      @Override
      public AutomationPlugin instance() {
        return plugin;
      }

      @Override
      public PluginContext context() {
        return context;
      }
    };
  }
}
