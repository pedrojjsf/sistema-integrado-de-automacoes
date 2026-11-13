package com.accord.sia.core;

import com.accord.sia.api.Actor;
import com.accord.sia.api.AutomationDescriptor;
import com.accord.sia.api.InvalidPluginException;
import com.accord.sia.api.Parameters;
import com.accord.sia.api.PluginLoadException;
import com.accord.sia.api.RunId;
import com.accord.sia.core.config.SiaConfig;
import com.accord.sia.core.context.PluginContextFactory;
import com.accord.sia.core.event.InProcessEventBus;
import com.accord.sia.core.manifest.PluginManifest;
import com.accord.sia.core.persistence.Database;
import com.accord.sia.core.persistence.SchemaMigrator;
import com.accord.sia.core.persistence.SingleWriter;
import com.accord.sia.core.plugin.LoadedPlugin;
import com.accord.sia.core.plugin.PluginCandidate;
import com.accord.sia.core.plugin.PluginDiscovery;
import com.accord.sia.core.plugin.PluginLoader;
import com.accord.sia.core.plugin.PluginRegistry;
import com.accord.sia.core.process.ProcessAllowlist;
import com.accord.sia.core.run.ExecutionEngine;
import com.accord.sia.core.run.RunOutcome;
import com.accord.sia.core.run.RunRecord;
import com.accord.sia.core.run.RunRepository;
import com.accord.sia.core.run.RunRequest;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Núcleo montado: banco migrado, plugins carregados, engine pronto.
 *
 * <p>E o composition root compartilhado entre o daemon HTTP e os testes — assim o caminho
 * exercitado no teste e o mesmo que roda em producao.
 */
public final class SiaCore implements AutoCloseable {

  private static final Logger LOG = Logger.getLogger(SiaCore.class.getName());

  private final SiaConfig config;
  private final Path databasePath;
  private final Database database;
  private final SingleWriter writer;
  private final InProcessEventBus events;
  private final PluginRegistry registry;
  private final ExecutionEngine engine;
  private final RunRepository repository;
  private final List<String> loadFailures;

  private SiaCore(
      SiaConfig config,
      Path databasePath,
      Database database,
      SingleWriter writer,
      InProcessEventBus events,
      PluginRegistry registry,
      ExecutionEngine engine,
      RunRepository repository,
      List<String> loadFailures) {
    this.config = config;
    this.databasePath = databasePath;
    this.database = database;
    this.writer = writer;
    this.events = events;
    this.registry = registry;
    this.engine = engine;
    this.repository = repository;
    this.loadFailures = List.copyOf(loadFailures);
  }

  public static SiaCore start(SiaConfig config) throws IOException {
    Path databasePath = Path.of(config.database().path());
    Database database = Database.open(databasePath);
    new SchemaMigrator().migrate(database);
    SingleWriter writer = new SingleWriter();
    InProcessEventBus events = new InProcessEventBus();
    RunRepository repository = new RunRepository(database, writer, Clock.systemUTC());
    ExecutionEngine engine =
        new ExecutionEngine(repository, Clock.systemUTC(), 4, Duration.ofSeconds(30), null);

    Map<String, ProcessAllowlist.Spec> specs = new LinkedHashMap<>();
    config
        .processes()
        .forEach((name, process) ->
            specs.put(
                name,
                new ProcessAllowlist.Spec(
                    resolve(process.command()), Duration.ofMinutes(process.timeoutMinutes()))));
    PluginContextFactory contexts =
        new PluginContextFactory(
            database, writer, ProcessAllowlist.ofSpecs(specs), events, Clock.systemUTC());

    PluginRegistry registry = new PluginRegistry();
    List<String> failures = new ArrayList<>();
    loadPlugins(Path.of(config.plugins().directory()), registry, contexts, failures);

    return new SiaCore(
        config, databasePath, database, writer, events, registry, engine, repository, failures);
  }

  private static void loadPlugins(
      Path directory,
      PluginRegistry registry,
      PluginContextFactory contexts,
      List<String> failures) {
    List<PluginCandidate> candidates;
    try {
      candidates = new PluginDiscovery().discover(directory);
    } catch (IOException | InvalidPluginException error) {
      failures.add("discovery: " + error.getMessage());
      return;
    }
    PluginLoader loader = new PluginLoader();
    for (PluginCandidate candidate : candidates) {
      try {
        LoadedPlugin plugin = loader.load(candidate);
        PluginManifest manifest = plugin.manifest();
        plugin.initialize(contexts.forPlugin(manifest, Map.of()));
        plugin.enable();
        registry.register(plugin);
      } catch (InvalidPluginException | PluginLoadException | IOException error) {
        failures.add(candidate.directory().getFileName() + ": " + error.getMessage());
        LOG.log(Level.WARNING, "plugin nao carregado: {0}", error.getMessage());
      } catch (RuntimeException error) {
        failures.add(candidate.directory().getFileName() + ": " + error.getMessage());
      }
    }
  }

  /** Substitui ${java.home} e ${env:VAR} no comando configurado. */
  private static List<String> resolve(List<String> command) {
    return command.stream().map(SiaCore::resolveToken).toList();
  }

  private static String resolveToken(String value) {
    String resolved = value.replace("${java.home}", System.getProperty("java.home"));
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile("\\$\\{env:([A-Za-z0-9_]+)}").matcher(resolved);
    StringBuilder buffer = new StringBuilder();
    while (matcher.find()) {
      String replacement = System.getenv(matcher.group(1));
      matcher.appendReplacement(
          buffer, java.util.regex.Matcher.quoteReplacement(replacement == null ? "" : replacement));
    }
    matcher.appendTail(buffer);
    return buffer.toString();
  }

  public Path databasePath() {
    return databasePath;
  }

  public SiaConfig config() {
    return config;
  }

  public List<LoadedPlugin> plugins() {
    return List.copyOf(registry.all());
  }

  public Optional<LoadedPlugin> plugin(String pluginId) {
    return registry.find(pluginId);
  }

  public List<String> loadFailures() {
    return loadFailures;
  }

  /** Catalogo agregado, so de plugins habilitados. */
  public List<AutomationDescriptor> automations() {
    List<AutomationDescriptor> descriptors = new ArrayList<>();
    for (LoadedPlugin plugin : registry.all()) {
      plugin.instance().automations().forEach(automation -> descriptors.add(automation.describe()));
    }
    return List.copyOf(descriptors);
  }

  /** Plugin dono de uma automacao, para o engine saber onde executa-la. */
  public Optional<LoadedPlugin> pluginOf(String automationId) {
    return registry.all().stream()
        .filter(plugin ->
            plugin.instance().automations().stream()
                .anyMatch(automation -> automation.id().equals(automationId)))
        .findFirst();
  }

  public RunOutcome execute(String automationId, Parameters params, Actor actor) {
    LoadedPlugin plugin =
        pluginOf(automationId)
            .orElseThrow(
                () -> new IllegalArgumentException("automacao desconhecida: " + automationId));
    return engine.execute(plugin, RunRequest.manual(automationId, params, actor));
  }

  public RunId submit(String automationId, Parameters params, Actor actor, String trigger) {
    LoadedPlugin plugin =
        pluginOf(automationId)
            .orElseThrow(
                () -> new IllegalArgumentException("automacao desconhecida: " + automationId));
    return engine.submit(plugin, new RunRequest(automationId, params, actor, trigger, null));
  }

  public Optional<RunRecord> run(RunId runId) {
    return repository.find(runId);
  }

  public List<RunRecord> runs(int limit) {
    return repository.recent(limit);
  }

  public List<String> logs(RunId runId) {
    return repository.logs(runId);
  }

  public boolean cancel(RunId runId) {
    return engine.cancel(runId);
  }

  public int runningCount() {
    return engine.runningCount();
  }

  public RunRepository repository() {
    return repository;
  }

  public ExecutionEngine engine() {
    return engine;
  }

  public InProcessEventBus events() {
    return events;
  }

  @Override
  public void close() {
    registry.unloadAll();
    engine.close();
    writer.close();
  }
}
