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
import com.accord.sia.core.manifest.ManifestLoader;
import com.accord.sia.core.manifest.PluginKind;
import com.accord.sia.core.manifest.PluginManifest;
import com.accord.sia.core.persistence.Database;
import com.accord.sia.core.persistence.SchemaMigrator;
import com.accord.sia.core.persistence.SingleWriter;
import com.accord.sia.core.plugin.LoadedPlugin;
import com.accord.sia.core.plugin.PluginCandidate;
import com.accord.sia.core.plugin.PluginDiscovery;
import com.accord.sia.core.plugin.PluginLoader;
import com.accord.sia.core.plugin.PluginPackage;
import com.accord.sia.core.plugin.PluginRegistry;
import com.accord.sia.core.process.ProcessAllowlist;
import com.accord.sia.core.run.ExecutionEngine;
import com.accord.sia.core.run.IdempotencyRepository;
import com.accord.sia.core.run.RunEventBus;
import com.accord.sia.core.run.RunOutcome;
import com.accord.sia.core.run.RunRecord;
import com.accord.sia.core.run.RunRepository;
import com.accord.sia.core.run.RunRequest;
import com.accord.sia.core.schedule.Schedule;
import com.accord.sia.core.schedule.ScheduleRepository;
import com.accord.sia.core.schedule.SchedulerService;
import com.accord.sia.core.schedule.Trigger;
import com.accord.sia.core.workflow.StepExecutor;
import com.accord.sia.core.workflow.WorkflowDefinition;
import com.accord.sia.core.workflow.WorkflowEngine;
import com.accord.sia.core.workflow.WorkflowLoader;
import com.accord.sia.core.workflow.WorkflowRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
 * Núcleo montado: banco migrado, lock de instancia, plugins carregados, engine, scheduler e
 * workflows.
 *
 * <p>E o composition root compartilhado entre o daemon HTTP e os testes — o caminho exercitado no
 * teste e o mesmo que roda em producao.
 */
public final class SiaCore implements AutoCloseable {

  private static final Logger LOG = Logger.getLogger(SiaCore.class.getName());

  private final SiaConfig config;
  private final Path databasePath;
  private final Path pluginsDirectory;
  private final Path workflowsDirectory;
  private final Database database;
  private final SingleWriter writer;
  private final InProcessEventBus events;
  private final RunEventBus runEvents;
  private final PluginRegistry registry;
  private final ExecutionEngine engine;
  private final RunRepository repository;
  private final IdempotencyRepository idempotency;
  private final PluginContextFactory contexts;
  private final InstanceLock lock;
  private final List<String> loadFailures;
  private volatile SchedulerService scheduler;
  private volatile WorkflowEngine workflowEngine;
  private volatile WorkflowRepository workflowRepository;

  private SiaCore(
      SiaConfig config,
      Path databasePath,
      Path pluginsDirectory,
      Path workflowsDirectory,
      Database database,
      SingleWriter writer,
      InProcessEventBus events,
      RunEventBus runEvents,
      PluginRegistry registry,
      ExecutionEngine engine,
      RunRepository repository,
      IdempotencyRepository idempotency,
      PluginContextFactory contexts,
      InstanceLock lock,
      List<String> loadFailures) {
    this.config = config;
    this.databasePath = databasePath;
    this.pluginsDirectory = pluginsDirectory;
    this.workflowsDirectory = workflowsDirectory;
    this.database = database;
    this.writer = writer;
    this.events = events;
    this.runEvents = runEvents;
    this.registry = registry;
    this.engine = engine;
    this.repository = repository;
    this.idempotency = idempotency;
    this.contexts = contexts;
    this.lock = lock;
    this.loadFailures = List.copyOf(loadFailures);
  }

  public static SiaCore start(SiaConfig config) throws IOException {
    Path databasePath = Path.of(config.database().path());
    InstanceLock lock = InstanceLock.acquire(databasePath);
    Database database = Database.open(databasePath);
    new SchemaMigrator().migrate(database);
    SingleWriter writer = new SingleWriter();
    InProcessEventBus events = new InProcessEventBus();
    RunEventBus runEvents = new RunEventBus();
    RunRepository repository = new RunRepository(database, writer, Clock.systemUTC());
    IdempotencyRepository idempotency =
        new IdempotencyRepository(database, writer, Clock.systemUTC());
    ExecutionEngine engine =
        new ExecutionEngine(
            repository, Clock.systemUTC(), 4, Duration.ofSeconds(30), null, runEvents);

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

    Path pluginsDirectory = Path.of(config.plugins().directory());
    PluginRegistry registry = new PluginRegistry();
    List<String> failures = new ArrayList<>();
    loadPlugins(pluginsDirectory, registry, contexts, failures);

    SiaCore core =
        new SiaCore(
            config,
            databasePath,
            pluginsDirectory,
            Path.of("workflows"),
            database,
            writer,
            events,
            runEvents,
            registry,
            engine,
            repository,
            idempotency,
            contexts,
            lock,
            failures);
    core.startWorkflow();
    core.startScheduler();
    return core;
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
        plugin.initialize(contexts.forPlugin(plugin.manifest(), Map.of()));
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

  private void startWorkflow() {
    this.workflowRepository = new WorkflowRepository(database, writer, Clock.systemUTC());
    StepExecutor executor =
        (automationId, params) -> {
          RunOutcome outcome = execute(automationId, params, Actor.system("workflow", null));
          return new StepExecutor.StepOutcome(
              outcome.succeeded(), outcome.errorCode(), outcome.message(), outcome.outputs());
        };
    this.workflowEngine = new WorkflowEngine(workflowRepository, executor);
  }

  private void startScheduler() {
    ScheduleRepository schedules = new ScheduleRepository(database, writer, Clock.systemUTC());
    this.scheduler =
        new SchedulerService(schedules, this::launchScheduled, events, Clock.systemUTC());
    scheduler.start();
  }

  private void launchScheduled(Schedule schedule, java.time.Instant fireTime) {
    Map<String, Object> params = new LinkedHashMap<>(schedule.params());
    submit(
        schedule.target(),
        Parameters.of(params),
        Actor.system("scheduler", schedule.owner()),
        "schedule");
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

  public RunEventBus runEvents() {
    return runEvents;
  }

  public List<AutomationDescriptor> automations() {
    List<AutomationDescriptor> descriptors = new ArrayList<>();
    for (LoadedPlugin plugin : registry.all()) {
      plugin.instance().automations().forEach(automation -> descriptors.add(automation.describe()));
    }
    return List.copyOf(descriptors);
  }

  public Optional<LoadedPlugin> pluginOf(String automationId) {
    return registry.all().stream()
        .filter(plugin ->
            plugin.instance().automations().stream()
                .anyMatch(automation -> automation.id().equals(automationId)))
        .findFirst();
  }

  public RunOutcome execute(String automationId, Parameters params, Actor actor) {
    return engine.execute(runtimeOf(automationId), RunRequest.manual(automationId, params, actor));
  }

  public RunId submit(String automationId, Parameters params, Actor actor, String trigger) {
    return engine.submit(
        runtimeOf(automationId), new RunRequest(automationId, params, actor, trigger, null));
  }

  /** Submissao com chave de idempotencia: retry do cliente devolve o mesmo run. */
  public RunId submit(
      String automationId, Parameters params, Actor actor, String trigger, String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return submit(automationId, params, actor, trigger);
    }
    Optional<RunId> existing = idempotency.find(idempotencyKey);
    if (existing.isPresent()) {
      return existing.get();
    }
    LoadedPlugin plugin = runtimeOf(automationId);
    RunId candidate = RunId.generate();
    if (!idempotency.register(idempotencyKey, candidate)) {
      return idempotency.find(idempotencyKey).orElse(candidate);
    }
    try {
      return engine.submit(
          plugin, new RunRequest(automationId, params, actor, trigger, null), candidate);
    } catch (RuntimeException error) {
      idempotency.forget(idempotencyKey);
      throw error;
    }
  }

  private LoadedPlugin runtimeOf(String automationId) {
    return pluginOf(automationId)
        .orElseThrow(() -> new IllegalArgumentException("automacao desconhecida: " + automationId));
  }

  public PluginValidation validatePlugin(Path jar) throws IOException, InvalidPluginException {
    PluginManifest manifest = PluginPackage.readManifest(jar);
    boolean mainPresent =
        manifest.kind() == PluginKind.JAVA && PluginPackage.hasMainClass(jar, manifest.main());
    return new PluginValidation(
        manifest.id(), manifest.version(), manifest.kind().wireName(), mainPresent);
  }

  public synchronized LoadedPlugin installPlugin(Path jar)
      throws IOException, InvalidPluginException, PluginLoadException {
    PluginManifest manifest = PluginPackage.readManifest(jar);
    if (registry.find(manifest.id()).isPresent()) {
      throw new IllegalStateException("plugin ja instalado: " + manifest.id());
    }
    Path directory = pluginsDirectory.resolve(manifest.id());
    Files.createDirectories(directory);
    Path target = directory.resolve("plugin.jar");
    Files.copy(jar, target, StandardCopyOption.REPLACE_EXISTING);
    Files.writeString(
        directory.resolve("plugin.yml"),
        PluginPackage.readEntry(jar, "plugin.yml"),
        StandardCharsets.UTF_8);

    LoadedPlugin plugin = null;
    try {
      plugin =
          new PluginLoader()
              .load(new PluginCandidate(directory, directory.resolve("plugin.yml"), target));
      plugin.initialize(contexts.forPlugin(manifest, Map.of()));
      plugin.enable();
      registry.register(plugin);
      return plugin;
    } catch (IOException | InvalidPluginException | PluginLoadException | RuntimeException error) {
      if (plugin != null) {
        plugin.unload();
      }
      Files.deleteIfExists(directory.resolve("plugin.yml"));
      Files.deleteIfExists(target);
      throw error;
    }
  }

  public synchronized void enablePlugin(String pluginId) {
    plugin(pluginId).orElseThrow(() -> unknown(pluginId)).enable();
  }

  public synchronized void disablePlugin(String pluginId) {
    plugin(pluginId).orElseThrow(() -> unknown(pluginId)).disable();
  }

  public synchronized LoadedPlugin reloadPlugin(String pluginId)
      throws IOException, InvalidPluginException, PluginLoadException {
    LoadedPlugin current = plugin(pluginId).orElseThrow(() -> unknown(pluginId));
    current.unload();
    registry.remove(pluginId);
    PluginCandidate candidate =
        new PluginDiscovery()
            .discover(pluginsDirectory).stream()
                .filter(entry -> idMatches(entry, pluginId))
                .findFirst()
                .orElseThrow(() -> unknown(pluginId));
    LoadedPlugin reloaded = new PluginLoader().load(candidate);
    reloaded.initialize(contexts.forPlugin(reloaded.manifest(), Map.of()));
    reloaded.enable();
    registry.register(reloaded);
    return reloaded;
  }

  private boolean idMatches(PluginCandidate candidate, String pluginId) {
    try {
      return new ManifestLoader().read(candidate.manifestFile()).id().equals(pluginId);
    } catch (IOException | InvalidPluginException error) {
      return false;
    }
  }

  private static IllegalArgumentException unknown(String pluginId) {
    return new IllegalArgumentException("plugin desconhecido: " + pluginId);
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

  public List<Schedule> schedules() {
    return scheduler.list();
  }

  public Schedule createSchedule(
      String target, Trigger trigger, Map<String, String> params, String owner) {
    return scheduler.create(target, trigger, params, owner);
  }

  public void setScheduleEnabled(String id, boolean enabled) {
    scheduler.setEnabled(id, enabled);
  }

  public boolean deleteSchedule(String id) {
    return scheduler.delete(id);
  }

  public SchedulerService scheduler() {
    return scheduler;
  }

  public List<String> workflowNames() throws IOException {
    if (!Files.isDirectory(workflowsDirectory)) {
      return List.of();
    }
    try (var files = Files.list(workflowsDirectory)) {
      return files
          .filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
          .map(path -> path.getFileName().toString().replaceFirst("\\.ya?ml$", ""))
          .sorted()
          .toList();
    }
  }

  public WorkflowEngine.WorkflowRun runWorkflow(String name, Map<String, Object> inputs)
      throws IOException {
    Path file = workflowFile(name);
    String yaml = Files.readString(file, StandardCharsets.UTF_8);
    WorkflowDefinition definition = new WorkflowLoader().parse(yaml);
    return workflowEngine.run(definition, yaml, inputs == null ? Map.of() : inputs);
  }

  public WorkflowEngine.WorkflowRun resumeWorkflow(String runId) throws IOException {
    return workflowEngine.resume(runId);
  }

  public WorkflowEngine.WorkflowRun workflowRun(String runId) {
    return workflowEngine.load(runId);
  }

  public List<WorkflowEngine.WorkflowRun> workflowRuns(int limit) {
    return workflowEngine.recent(limit);
  }

  private Path workflowFile(String name) {
    Path yml = workflowsDirectory.resolve(name + ".yml");
    if (Files.isRegularFile(yml)) {
      return yml;
    }
    Path yaml = workflowsDirectory.resolve(name + ".yaml");
    if (Files.isRegularFile(yaml)) {
      return yaml;
    }
    throw new IllegalArgumentException("workflow desconhecido: " + name);
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
    if (workflowEngine != null) {
      workflowEngine.close();
    }
    if (scheduler != null) {
      scheduler.close();
    }
    registry.unloadAll();
    engine.close();
    writer.close();
    lock.close();
  }

  /** Resultado da validacao de um pacote. */
  public record PluginValidation(
      String id, String version, String kind, boolean mainClassPresent) {}

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
}
