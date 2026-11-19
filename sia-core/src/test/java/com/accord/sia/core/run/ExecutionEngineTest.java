package com.accord.sia.core.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.accord.sia.api.Actor;
import com.accord.sia.api.Automation;
import com.accord.sia.api.AutomationCancelledException;
import com.accord.sia.api.AutomationContext;
import com.accord.sia.api.AutomationDescriptor;
import com.accord.sia.api.AutomationException;
import com.accord.sia.api.AutomationPlugin;
import com.accord.sia.api.AutomationResult;
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
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecutionEngineTest {

  @TempDir Path temp;

  private Database database;
  private SingleWriter writer;
  private RunRepository repository;
  private ExecutionEngine engine;

  @BeforeEach
  void setUp() throws IOException {
    database = Database.open(temp.resolve("sia.db"));
    new SchemaMigrator().migrate(database);
    writer = new SingleWriter();
    repository = new RunRepository(database, writer, Clock.systemUTC());
    engine =
        new ExecutionEngine(
            repository, Clock.systemUTC(), 2, Duration.ofMillis(200), Duration.ofSeconds(5));
  }

  @AfterEach
  void tearDown() {
    engine.close();
    writer.close();
  }

  @Test
  void sucessoRegistraExecucao() {
    RunOutcome outcome =
        engine.execute(
            runtime(automation("fake.ok", context -> AutomationResult.success())),
            request("fake.ok"));

    assertThat(outcome.status()).isEqualTo(RunStatus.SUCCESS);
    RunRecord record = repository.find(outcome.runId()).orElseThrow();
    assertThat(record.pluginId()).isEqualTo("fake-plugin");
    assertThat(record.automationId()).isEqualTo("fake.ok");
    assertThat(record.actor()).isEqualTo("sub-analista");
    assertThat(record.status()).isEqualTo(RunStatus.SUCCESS);
    assertThat(record.finishedAt()).isNotNull();
  }

  @Test
  void falhaGuardaErrorCode() {
    RunOutcome outcome =
        engine.execute(
            runtime(
                automation(
                    "fake.fail", context -> AutomationResult.failure("SMTP_DOWN", "sem smtp"))),
            request("fake.fail"));

    assertThat(outcome.status()).isEqualTo(RunStatus.FAILURE);
    assertThat(repository.find(outcome.runId()).orElseThrow().errorCode()).isEqualTo("SMTP_DOWN");
  }

  @Test
  void excecaoViraFalhaComCodigo() {
    RunOutcome outcome =
        engine.execute(
            runtime(
                automation(
                    "fake.boom",
                    context -> {
                      throw new AutomationException("BOOM", "estourou");
                    })),
            request("fake.boom"));

    assertThat(outcome.status()).isEqualTo(RunStatus.FAILURE);
    assertThat(outcome.errorCode()).isEqualTo("BOOM");
  }

  @Test
  void runtimeExceptionViraErroInesperado() {
    RunOutcome outcome =
        engine.execute(
            runtime(
                automation(
                    "fake.crash",
                    context -> {
                      throw new IllegalStateException("bug do plugin");
                    })),
            request("fake.crash"));

    assertThat(outcome.status()).isEqualTo(RunStatus.FAILURE);
    assertThat(outcome.errorCode()).isEqualTo("UNEXPECTED_ERROR");
  }

  @Test
  void timeoutMarcaTimedOut() {
    RunOutcome outcome =
        engine.execute(
            runtime(
                automation(
                    "fake.slow",
                    context -> {
                      pause(10_000);
                      return AutomationResult.success();
                    })),
            request("fake.slow").withTimeout(Duration.ofMillis(300)));

    assertThat(outcome.status()).isEqualTo(RunStatus.TIMED_OUT);
    assertThat(outcome.duration()).isLessThan(Duration.ofSeconds(8));
  }

  @Test
  void cancelamentoMarcaCancelled() throws Exception {
    // sem o latch, sob carga o run pode ainda nao estar ativo quando cancelAll roda
    java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
    PluginRuntime runtime =
        runtime(
            automation(
                "fake.wait",
                context -> {
                  started.countDown();
                  while (!context.cancellation().isCancelled()) {
                    pause(20);
                  }
                  context.cancellation().throwIfCancelled();
                  return AutomationResult.success();
                }));

    try (ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<RunOutcome> future =
          callers.submit(() -> engine.execute(runtime, request("fake.wait")));
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(engine.cancelAll()).isEqualTo(1);
      RunOutcome outcome = future.get(5, TimeUnit.SECONDS);

      assertThat(outcome.status()).isEqualTo(RunStatus.CANCELLED);
      assertThat(outcome.errorCode()).isEqualTo("RUN_CANCELLED");
    }
  }

  @Test
  void bulkheadRejeitaExcessoDeConcorrencia() throws Exception {
    try (ExecutionEngine single =
            new ExecutionEngine(
                repository, Clock.systemUTC(), 1, Duration.ofMillis(150), Duration.ofSeconds(10));
        ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor()) {
      PluginRuntime runtime =
          runtime(
              automation(
                  "fake.block",
                  context -> {
                    while (!context.cancellation().isCancelled()) {
                      pause(20);
                    }
                    context.cancellation().throwIfCancelled();
                    return AutomationResult.success();
                  }));

      Future<RunOutcome> first =
          callers.submit(() -> single.execute(runtime, request("fake.block")));
      Thread.sleep(200);
      RunOutcome second = single.execute(runtime, request("fake.block"));

      assertThat(second.status()).isEqualTo(RunStatus.REJECTED);
      assertThat(second.errorCode()).isEqualTo("CONCURRENCY_LIMIT");

      single.cancelAll();
      assertThat(first.get(5, TimeUnit.SECONDS).status()).isEqualTo(RunStatus.CANCELLED);
    }
  }

  @Test
  void outputsFicamSoComAsChaves() {
    RunOutcome outcome =
        engine.execute(
            runtime(
                automation(
                    "fake.out",
                    context -> AutomationResult.success(Map.of("protocolo", "PROTO-123"), null))),
            request("fake.out"));

    assertThat(outcome.outputs()).containsEntry("protocolo", "PROTO-123");
    RunRecord record = repository.find(outcome.runId()).orElseThrow();
    assertThat(record.outputsKeys()).containsExactly("protocolo");
    assertThat(record.message()).isNull();
  }

  @Test
  void recusaPluginNaoHabilitado() {
    PluginRuntime runtime =
        runtime(
            PluginState.INITIALIZED, automation("fake.ok", context -> AutomationResult.success()));

    assertThatThrownBy(() -> engine.execute(runtime, request("fake.ok")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ENABLED");
  }

  @Test
  void recusaAutomacaoInexistente() {
    assertThatThrownBy(() ->
            engine.execute(
                runtime(automation("fake.ok", context -> AutomationResult.success())),
                request("fake.naoexiste")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fake.naoexiste");
  }

  @Test
  void registraLogDaAutomacao() {
    RunOutcome outcome =
        engine.execute(
            runtime(
                automation(
                    "fake.log",
                    context -> {
                      context.log().info("executando passo 1");
                      context.progress().progress(50, "meio");
                      return AutomationResult.success();
                    })),
            request("fake.log"));

    List<String> logs = repository.logs(outcome.runId());
    assertThat(logs).anyMatch(line -> line.contains("executando passo 1"));
    assertThat(logs).anyMatch(line -> line.contains("50%"));
  }

  private static RunRequest request(String automationId) {
    return RunRequest.manual(
        automationId,
        Parameters.of("cpf", "123.456.789-09"),
        Actor.user("sub-analista", "Analista"));
  }

  /** Dorme sem quebrar o contrato de {@code execute}, que nao declara InterruptedException. */
  private static void pause(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new AutomationCancelledException();
    }
  }

  @FunctionalInterface
  private interface Body {
    AutomationResult run(AutomationContext context) throws AutomationException;
  }

  private static Automation automation(String id, Body body) {
    return new Automation() {
      @Override
      public AutomationDescriptor describe() {
        return AutomationDescriptor.of(
            id, "Teste", "automacao de teste", List.of(ParamSpecFactory.sensitive("cpf")));
      }

      @Override
      public AutomationResult execute(AutomationContext context) throws AutomationException {
        return body.run(context);
      }
    };
  }

  private static PluginRuntime runtime(Automation... automations) {
    return runtime(PluginState.ENABLED, automations);
  }

  private static PluginRuntime runtime(PluginState state, Automation... automations) {
    AutomationPlugin plugin =
        new AutomationPlugin() {
          @Override
          public PluginMetadata metadata() {
            return new PluginMetadata("fake-plugin", "Fake", "1.0.0", "1.0");
          }

          @Override
          public List<Automation> automations() {
            return List.of(automations);
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
        return state;
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

  /** Atalho para nao repetir a construcao de ParamSpec nos testes. */
  private static final class ParamSpecFactory {
    private ParamSpecFactory() {}

    static com.accord.sia.api.ParamSpec sensitive(String name) {
      return com.accord.sia.api.ParamSpec.optional(
              name, com.accord.sia.api.ParamType.STRING, "parametro sensivel")
          .asSensitive();
    }
  }
}
