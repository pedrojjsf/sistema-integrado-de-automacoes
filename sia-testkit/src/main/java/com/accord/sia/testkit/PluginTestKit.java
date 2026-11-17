package com.accord.sia.testkit;

import com.accord.sia.api.Actor;
import com.accord.sia.api.AutomationPlugin;
import com.accord.sia.api.Parameters;
import com.accord.sia.api.PluginContext;
import com.accord.sia.api.ProcessRunner;
import com.accord.sia.api.RunId;
import com.accord.sia.core.config.MapConfiguration;
import com.accord.sia.core.event.InProcessEventBus;
import com.accord.sia.core.logging.Slf4jLog;
import com.accord.sia.core.persistence.Database;
import com.accord.sia.core.persistence.SchemaMigrator;
import com.accord.sia.core.persistence.SingleWriter;
import com.accord.sia.core.plugin.PluginRuntime;
import com.accord.sia.core.plugin.PluginState;
import com.accord.sia.core.process.DefaultProcessRunner;
import com.accord.sia.core.process.PermissionCheck;
import com.accord.sia.core.process.ProcessAllowlist;
import com.accord.sia.core.run.ExecutionEngine;
import com.accord.sia.core.run.RunEventBus;
import com.accord.sia.core.run.RunOutcome;
import com.accord.sia.core.run.RunRepository;
import com.accord.sia.core.run.RunRequest;
import com.accord.sia.core.secrets.EnvSecretAccess;
import com.accord.sia.core.storage.SqliteStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Harness para autores de plugin: sobe um Core minimo em memoria, inicializa o plugin e executa
 * automacoes sem daemon, sem rede e sem HTTP.
 *
 * <pre>{@code
 * try (PluginTestKit kit = PluginTestKit.start(new MeuPlugin(), Permissions.all())) {
 *   kit.run("meuplugin.enviar", Parameters.of("destino", "x"))
 *      .assertSuccess()
 *      .assertNoSensitiveData("segredo");
 * }
 * }</pre>
 */
public final class PluginTestKit implements AutoCloseable {

  private final AutomationPlugin plugin;
  private final Path workingDirectory;
  private final SingleWriter writer;
  private final ExecutionEngine engine;
  private final RunRepository repository;
  private final PluginContext context;
  private final PluginRuntime runtime;

  private PluginTestKit(
      AutomationPlugin plugin,
      Path workingDirectory,
      SingleWriter writer,
      ExecutionEngine engine,
      RunRepository repository,
      PluginContext context) {
    this.plugin = plugin;
    this.workingDirectory = workingDirectory;
    this.writer = writer;
    this.engine = engine;
    this.repository = repository;
    this.context = context;
    this.runtime =
        new PluginRuntime() {
          @Override
          public String id() {
            return plugin.metadata().id();
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

  public static PluginTestKit start(AutomationPlugin plugin) {
    return start(plugin, Permissions.none());
  }

  public static PluginTestKit start(AutomationPlugin plugin, Permissions permissions) {
    return start(plugin, permissions, Map.of());
  }

  public static PluginTestKit start(
      AutomationPlugin plugin, Permissions permissions, Map<String, String> config) {
    try {
      Path directory = Files.createTempDirectory("sia-testkit");
      Database database = Database.open(directory.resolve("sia.db"));
      new SchemaMigrator().migrate(database);
      SingleWriter writer = new SingleWriter();
      RunRepository repository = new RunRepository(database, writer, Clock.systemUTC());
      ExecutionEngine engine =
          new ExecutionEngine(
              repository,
              Clock.systemUTC(),
              4,
              Duration.ofSeconds(5),
              Duration.ofMinutes(2),
              new RunEventBus());

      PermissionCheck guard = permissionGuard(plugin.metadata().id(), permissions);
      PluginContext context =
          new PluginContext(
              plugin.metadata().id(),
              new Slf4jLog(plugin.metadata().id()),
              new MapConfiguration(config),
              new SqliteStorage(database, writer, plugin.metadata().id(), Clock.systemUTC(), guard),
              new InProcessEventBus(),
              new EnvSecretAccess(plugin.metadata().id(), guard),
              new DefaultProcessRunner(ProcessAllowlist.ofSpecs(Map.of()), guard),
              Clock.systemUTC());
      plugin.initialize(context);

      return new PluginTestKit(plugin, directory, writer, engine, repository, context);
    } catch (IOException error) {
      throw new UncheckedIOException("nao foi possivel subir o TestKit", error);
    }
  }

  private static PermissionCheck permissionGuard(String pluginId, Permissions permissions) {
    return permission -> {
      if (!permissions.allows(permission)) {
        throw new com.accord.sia.api.PermissionDeniedException(permission.wireName(), pluginId);
      }
    };
  }

  public PluginContext context() {
    return context;
  }

  public RunResult run(String automationId) {
    return run(automationId, Parameters.empty());
  }

  public RunResult run(String automationId, Map<String, Object> params) {
    return run(automationId, Parameters.of(params == null ? Map.of() : params));
  }

  public RunResult run(String automationId, Parameters params) {
    RunOutcome outcome =
        engine.execute(
            runtime,
            RunRequest.manual(
                automationId,
                params == null ? Parameters.empty() : params,
                Actor.user("sub-teste", "Teste")));
    RunId runId = outcome.runId();
    return new RunResult(
        runId,
        outcome.status(),
        outcome.errorCode(),
        outcome.message(),
        outcome.outputs(),
        repository.logs(runId));
  }

  /** Executa e devolve o erro esperado, sem derrubar o teste com excecao. */
  public RunResult runExpectingFailure(String automationId, Parameters params) {
    try {
      return run(automationId, params);
    } catch (IllegalStateException | IllegalArgumentException error) {
      throw new AssertionError("falha inesperada ao executar: " + error.getMessage(), error);
    }
  }

  public ProcessRunner processRunner() {
    return context.processes();
  }

  @Override
  public void close() {
    try {
      plugin.shutdown();
    } finally {
      engine.close();
      writer.close();
      deleteRecursively(workingDirectory);
    }
  }

  private static void deleteRecursively(Path directory) {
    try (Stream<Path> paths = Files.walk(directory)) {
      paths
          .sorted(Comparator.reverseOrder())
          .forEach(path -> {
            try {
              Files.deleteIfExists(path);
            } catch (IOException ignored) {
              // melhor esforco: arquivo temporario
            }
          });
    } catch (IOException ignored) {
      // melhor esforco
    }
  }
}
