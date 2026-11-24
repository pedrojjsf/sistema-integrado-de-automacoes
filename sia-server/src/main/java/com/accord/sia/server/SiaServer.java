package com.accord.sia.server;

import com.accord.sia.api.Actor;
import com.accord.sia.api.AutomationDescriptor;
import com.accord.sia.api.InvalidPluginException;
import com.accord.sia.api.ParamSpec;
import com.accord.sia.api.Parameters;
import com.accord.sia.api.PluginLoadException;
import com.accord.sia.api.RunId;
import com.accord.sia.auth.AuthorizationException;
import com.accord.sia.auth.Authorizer;
import com.accord.sia.auth.Permission;
import com.accord.sia.auth.Principal;
import com.accord.sia.auth.SiaRole;
import com.accord.sia.core.SiaCore;
import com.accord.sia.core.config.SiaConfig;
import com.accord.sia.core.plugin.LoadedPlugin;
import com.accord.sia.core.plugin.PluginUi;
import com.accord.sia.core.run.RunRecord;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Daemon HTTP do SIA.
 *
 * <p>Regras que valem em toda rota: token obrigatorio, permissao por papel, corpo de requisicao
 * nunca logado e resposta de erro sem stack trace.
 */
public final class SiaServer implements AutoCloseable {

  private final SiaCore core;
  private final Authorizer authorizer = new Authorizer();
  private final Map<String, Principal> principals;
  private final String authMode;
  private final Principal anonymous;
  private final java.time.ZoneId zone;
  private final Javalin app;

  private SiaServer(
      SiaCore core,
      Map<String, Principal> principals,
      String authMode,
      Principal anonymous,
      java.time.ZoneId zone) {
    this.core = core;
    this.principals = Map.copyOf(principals);
    this.authMode = authMode;
    this.anonymous = anonymous;
    this.zone = zone;
    this.app =
        Javalin.create(config -> {
          config.showJavalinBanner = false;
          config.staticFiles.add(staticFiles -> {
            staticFiles.hostedPath = "/";
            staticFiles.directory = "/web";
            staticFiles.location = io.javalin.http.staticfiles.Location.CLASSPATH;
          });
        });
    registerRoutes();
  }

  public static SiaServer create(SiaConfig config) throws IOException {
    String mode = config.auth().mode().toLowerCase(Locale.ROOT);
    if (!"dev".equals(mode) && !"none".equals(mode)) {
      throw new IllegalStateException(
          "auth.mode=" + mode + " ainda nao suportado; use none (uso local) ou dev");
    }
    java.time.ZoneId zone = config.scheduler().zoneId();
    if ("none".equals(mode)) {
      // sem login: so aceitavel quando o daemon nao sai da maquina
      if (!loopback(config.server().host())) {
        throw new IllegalStateException(
            "auth.mode=none exige bind em loopback (127.0.0.1/localhost); host atual: "
                + config.server().host());
      }
      Principal local = Principal.user("local", "Operador local", Set.of(SiaRole.ADMIN));
      return new SiaServer(coreWith(config), Map.of(), mode, local, zone);
    }
    Map<String, Principal> principals = new LinkedHashMap<>();
    config
        .auth()
        .tokens()
        .forEach((token, definition) ->
            principals.put(
                token,
                Principal.user(
                    definition.subject(), definition.name(), roles(definition.roles()))));
    return new SiaServer(coreWith(config), principals, mode, null, zone);
  }

  private static boolean loopback(String host) {
    return host == null
        || host.isBlank()
        || "127.0.0.1".equals(host)
        || "localhost".equalsIgnoreCase(host)
        || "::1".equals(host);
  }

  private static SiaCore coreWith(SiaConfig config) throws IOException {
    return SiaCore.start(config);
  }

  private static Set<SiaRole> roles(List<String> names) {
    return names.stream()
        .map(name -> SiaRole.valueOf(name.trim().toUpperCase(Locale.ROOT)))
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  public SiaServer start(String host, int port) {
    app.start(host, port);
    return this;
  }

  public int port() {
    return app.port();
  }

  public SiaCore core() {
    return core;
  }

  @Override
  public void close() {
    app.stop();
    core.close();
  }

  private void registerRoutes() {
    app.before(
        "/api/health", ctx -> authorize(ctx, Permission.HEALTH_READ, Permission.HEALTH_READ));
    app.before(
        "/api/plugins", ctx -> authorize(ctx, Permission.PLUGIN_READ, Permission.PLUGIN_MANAGE));
    app.before(
        "/api/plugins/*", ctx -> authorize(ctx, Permission.PLUGIN_READ, Permission.PLUGIN_MANAGE));
    app.before(
        "/api/automations", ctx -> authorize(ctx, Permission.PLUGIN_READ, Permission.PLUGIN_READ));
    app.before("/api/runs", ctx -> authorize(ctx, Permission.RUN_READ, Permission.RUN_EXECUTE));
    app.before("/api/runs/*", ctx -> authorize(ctx, Permission.RUN_READ, Permission.RUN_EXECUTE));

    app.get("/", ctx -> ctx.redirect("/index.html"));
    app.get("/api/health", this::health);
    app.get("/api/plugins", this::plugins);
    app.get("/api/plugins/{id}", this::plugin);
    app.get("/api/plugins/{id}/ui/*", this::pluginUi);
    app.get("/api/automations", this::automations);
    app.post("/api/runs", this::createRun);
    app.get("/api/runs/{id}/events", this::events);
    app.get("/api/runs", this::runs);
    app.get("/api/runs/{id}", this::run);
    app.get("/api/runs/{id}/logs", this::logs);
    app.post("/api/runs/{id}/cancel", this::cancel);
    app.post("/api/plugins/validate", this::validatePlugin);
    app.post("/api/plugins/install", this::installPlugin);
    app.post("/api/plugins/{id}/enable", ctx -> changeState(ctx, true));
    app.post("/api/plugins/{id}/disable", ctx -> changeState(ctx, false));
    app.post("/api/plugins/{id}/reload", this::reloadPlugin);
    app.post("/api/plugins/{id}/uninstall", this::uninstallPlugin);
    app.before(
        "/api/schedules",
        ctx -> authorize(ctx, Permission.SCHEDULE_MANAGE, Permission.SCHEDULE_MANAGE));
    app.before(
        "/api/schedules/*",
        ctx -> authorize(ctx, Permission.SCHEDULE_MANAGE, Permission.SCHEDULE_MANAGE));
    app.get("/api/schedules", this::schedules);
    app.post("/api/schedules", this::createSchedule);
    app.post("/api/schedules/{id}/enable", ctx -> setScheduleState(ctx, true));
    app.post("/api/schedules/{id}/disable", ctx -> setScheduleState(ctx, false));
    app.delete("/api/schedules/{id}", this::deleteSchedule);
    app.before(
        "/api/workflows*", ctx -> authorize(ctx, Permission.RUN_READ, Permission.RUN_EXECUTE));
    app.get("/api/workflows", this::workflows);
    app.post("/api/workflows/{name}/runs", this::runWorkflow);
    app.get("/api/workflows/runs", this::workflowRuns);
    app.get("/api/workflows/runs/{id}", this::workflowRun);
    app.post("/api/workflows/runs/{id}/resume", this::resumeWorkflow);
    app.get("/openapi.yaml", this::openapi);

    app.exception(
        AuthorizationException.class,
        (error, ctx) -> {
          boolean forbidden = error.reason() == AuthorizationException.Reason.FORBIDDEN;
          ctx.status(forbidden ? 403 : 401).json(Map.of("error", error.getMessage()));
        });
    app.exception(
        io.javalin.http.BadRequestResponse.class,
        (error, ctx) -> ctx.status(400).json(Map.of("error", "corpo da requisicao invalido")));
    app.exception(
        com.fasterxml.jackson.core.JsonProcessingException.class,
        (error, ctx) ->
            ctx.status(400).json(Map.of("error", "JSON invalido: " + error.getOriginalMessage())));
    app.exception(
        IllegalStateException.class,
        (error, ctx) -> ctx.status(409).json(Map.of("error", error.getMessage())));
    app.exception(
        IllegalArgumentException.class,
        (error, ctx) -> ctx.status(400).json(Map.of("error", error.getMessage())));
    app.exception(
        Exception.class,
        (error, ctx) -> {
          ctx.status(500).json(Map.of("error", "erro interno"));
        });
  }

  private void authorize(Context ctx, Permission read, Permission write) {
    Principal principal;
    if ("none".equals(authMode)) {
      principal = anonymous;
    } else {
      String token = bearer(ctx);
      principal =
          principals.entrySet().stream()
              .filter(entry -> constantTimeEquals(entry.getKey(), token))
              .map(Map.Entry::getValue)
              .findFirst()
              .orElseThrow(() ->
                  new AuthorizationException(
                      AuthorizationException.Reason.UNAUTHENTICATED, "token ausente ou invalido"));
    }
    Permission required = "GET".equalsIgnoreCase(ctx.method().name()) ? read : write;
    authorizer.require(principal, required);
    ctx.attribute("principal", principal);
  }

  private static String bearer(Context ctx) {
    String header = Optional.ofNullable(ctx.header("Authorization")).orElse("");
    return header.toLowerCase(Locale.ROOT).startsWith("bearer ") ? header.substring(7).trim() : "";
  }

  private static boolean constantTimeEquals(String expected, String provided) {
    if (provided == null || provided.isEmpty()) {
      return false;
    }
    return java.security.MessageDigest.isEqual(
        expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
        provided.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private void health(Context ctx) {
    ctx.json(
        Map.of(
            "status",
            "ok",
            "version",
            "1.0.0",
            "auth",
            authMode,
            "plugins",
            core.plugins().size(),
            "running",
            core.runningCount(),
            "failures",
            core.loadFailures()));
  }

  private void plugins(Context ctx) {
    ctx.json(core.plugins().stream().map(this::pluginView).toList());
  }

  private void plugin(Context ctx) {
    String id = ctx.pathParam("id");
    LoadedPlugin plugin =
        core.plugin(id)
            .orElseThrow(() -> new IllegalArgumentException("plugin desconhecido: " + id));
    ctx.json(pluginView(plugin));
  }

  private Map<String, Object> pluginView(LoadedPlugin plugin) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("id", plugin.id());
    view.put("name", plugin.manifest().name());
    view.put("version", plugin.manifest().version());
    view.put("description", plugin.manifest().description());
    view.put("state", plugin.state().name());
    view.put("automations", plugin.instance().automations().stream().map(a -> a.id()).toList());
    var ui = plugin.manifest().ui();
    view.put(
        "ui",
        ui == null
            ? null
            : Map.of("entry", ui.entry(), "width", ui.width(), "height", ui.height()));
    return view;
  }

  /** Serve a interface propria do plugin, restrita aos arquivos dentro da pasta declarada. */
  private void pluginUi(Context ctx) {
    String id = ctx.pathParam("id");
    LoadedPlugin plugin =
        core.plugin(id)
            .orElseThrow(() -> new IllegalArgumentException("plugin desconhecido: " + id));
    String marker = "/ui/";
    int index = ctx.path().indexOf(marker);
    String path = index < 0 ? "" : ctx.path().substring(index + marker.length());
    try {
      PluginUi.Resource resource = PluginUi.read(plugin, path);
      ctx.contentType(resource.contentType());
      ctx.header("Cache-Control", "no-cache");
      ctx.result(new java.io.ByteArrayInputStream(resource.content()));
    } catch (java.io.IOException error) {
      throw new IllegalArgumentException(error.getMessage());
    }
  }

  private void automations(Context ctx) {
    ctx.json(core.automations().stream().map(this::automationView).toList());
  }

  private Map<String, Object> automationView(AutomationDescriptor descriptor) {
    return Map.of(
        "id", descriptor.id(),
        "name", descriptor.name(),
        "description", descriptor.description() == null ? "" : descriptor.description(),
        "persistOutputs", descriptor.persistOutputs(),
        "params", descriptor.params().stream().map(this::paramView).toList());
  }

  private Map<String, Object> paramView(ParamSpec spec) {
    return Map.of(
        "name", spec.name(),
        "type", spec.type().name(),
        "required", spec.required(),
        "sensitive", spec.sensitive());
  }

  private void createRun(Context ctx) {
    RunRequestPayload payload =
        ctx.bodyValidator(RunRequestPayload.class)
            .check(request -> request.automation() != null, "automation e obrigatorio")
            .get();
    Principal principal = ctx.attribute("principal");
    Actor actor = Actor.user(principal.subject(), principal.displayName());
    Parameters params = Parameters.of(payload.params() == null ? Map.of() : payload.params());

    // wait=true: executa e devolve o resultado com os valores de saida em memoria.
    // Usado pelo painel do plugin; o historico continua guardando apenas as chaves.
    if ("true".equalsIgnoreCase(ctx.queryParam("wait"))) {
      var outcome = core.execute(payload.automation(), params, actor);
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("runId", outcome.runId().value());
      body.put("automation", payload.automation());
      body.put("status", outcome.status().name());
      body.put("errorCode", outcome.errorCode());
      body.put("message", outcome.message());
      body.put("outputs", outcome.outputs());
      body.put("durationMillis", outcome.duration() == null ? null : outcome.duration().toMillis());
      ctx.json(body);
      return;
    }

    String idempotencyKey = ctx.header("Idempotency-Key");
    RunId runId =
        core.submit(
            payload.automation(), params, actor, RunRequestPayload.TRIGGER_API, idempotencyKey);
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      ctx.header("Idempotent-Replay", "true");
    }
    ctx.status(202).json(Map.of("runId", runId.value()));
  }

  private void runs(Context ctx) {
    int limit = parseInt(ctx.queryParam("limit"), 20);
    List<Map<String, Object>> view = core.runs(limit).stream().map(this::runView).toList();
    ctx.json(view);
  }

  private void run(Context ctx) {
    RunId runId = runId(ctx);
    RunRecord record =
        core.run(runId)
            .orElseThrow(() -> new IllegalArgumentException("execucao desconhecida: " + runId));
    ctx.json(runView(record));
  }

  private void logs(Context ctx) {
    ctx.json(core.logs(runId(ctx)));
  }

  private void cancel(Context ctx) {
    boolean cancelled = core.cancel(runId(ctx));
    ctx.json(Map.of("cancelled", cancelled));
  }

  private void events(Context ctx) {
    RunId runId = runId(ctx);
    core.run(runId)
        .orElseThrow(() -> new IllegalArgumentException("execucao desconhecida: " + runId));
    ctx.header("Content-Type", "text/event-stream");
    ctx.header("Cache-Control", "no-cache");
    ctx.header("X-Accel-Buffering", "no");
    try {
      java.io.OutputStream output = ctx.res().getOutputStream();
      java.util.concurrent.atomic.AtomicBoolean closed =
          new java.util.concurrent.atomic.AtomicBoolean();
      try (AutoCloseable subscription =
          core.runEvents()
              .subscribe(
                  runId.value(),
                  event -> {
                    if (closed.get()) {
                      return;
                    }
                    try {
                      SseWriter.write(output, event);
                    } catch (java.io.IOException error) {
                      closed.set(true);
                    }
                  })) {
        SseWriter.frame(output, "ready", "{}");
        long deadline = System.nanoTime() + 600_000_000_000L;
        while (!closed.get() && System.nanoTime() < deadline) {
          RunRecord record = core.run(runId).orElseThrow();
          if (record.status().isFinished()) {
            SseWriter.finished(output, record.status().name());
            break;
          }
          SseWriter.frame(output, "ping", "{}");
          Thread.sleep(500);
        }
      }
    } catch (java.lang.Exception error) {
      if (error instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void validatePlugin(Context ctx) throws java.io.IOException, InvalidPluginException {
    String path = ctx.bodyAsClass(PluginPathPayload.class).path();
    var validation = core.validatePlugin(java.nio.file.Path.of(path));
    ctx.json(
        Map.of(
            "id", validation.id(),
            "version", validation.version(),
            "kind", validation.kind(),
            "mainClassPresent", validation.mainClassPresent()));
  }

  private void installPlugin(Context ctx)
      throws java.io.IOException, InvalidPluginException, PluginLoadException {
    String path = ctx.bodyAsClass(PluginPathPayload.class).path();
    var plugin = core.installPlugin(java.nio.file.Path.of(path));
    ctx.status(201).json(pluginView(plugin));
  }

  private void changeState(Context ctx, boolean enable) {
    String id = ctx.pathParam("id");
    if (enable) {
      core.enablePlugin(id);
    } else {
      core.disablePlugin(id);
    }
    ctx.json(pluginView(core.plugin(id).orElseThrow()));
  }

  private void uninstallPlugin(Context ctx) {
    String id = ctx.pathParam("id");
    try {
      core.uninstallPlugin(id);
    } catch (java.io.IOException error) {
      throw new IllegalStateException("falha ao remover arquivos do plugin " + id, error);
    }
    ctx.json(Map.of("uninstalled", id));
  }

  private void reloadPlugin(Context ctx)
      throws java.io.IOException, InvalidPluginException, PluginLoadException {
    var plugin = core.reloadPlugin(ctx.pathParam("id"));
    ctx.json(pluginView(plugin));
  }

  private void workflows(Context ctx) {
    try {
      ctx.json(core.workflowNames());
    } catch (java.io.IOException error) {
      throw new IllegalStateException("falha ao listar workflows", error);
    }
  }

  private void runWorkflow(Context ctx) {
    String name = ctx.pathParam("name");
    WorkflowInputsPayload payload = ctx.bodyAsClass(WorkflowInputsPayload.class);
    try {
      var run = core.runWorkflow(name, payload.inputs() == null ? Map.of() : payload.inputs());
      ctx.status(202).json(workflowView(run));
    } catch (java.io.IOException error) {
      throw new IllegalArgumentException("workflow invalido: " + name);
    }
  }

  private void workflowRuns(Context ctx) {
    int limit = parseInt(ctx.queryParam("limit"), 20);
    ctx.json(core.workflowRuns(limit).stream().map(this::workflowView).toList());
  }

  private void workflowRun(Context ctx) {
    ctx.json(workflowView(core.workflowRun(ctx.pathParam("id"))));
  }

  private void resumeWorkflow(Context ctx) {
    try {
      ctx.json(workflowView(core.resumeWorkflow(ctx.pathParam("id"))));
    } catch (java.io.IOException error) {
      throw new IllegalArgumentException("nao foi possivel retomar: " + error.getMessage());
    }
  }

  private Map<String, Object> workflowView(
      com.accord.sia.core.workflow.WorkflowEngine.WorkflowRun run) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("id", run.id());
    view.put("name", run.name());
    view.put("status", run.status());
    view.put("nodes", run.nodeStatuses());
    return view;
  }

  private void schedules(Context ctx) {
    ctx.json(core.schedules().stream().map(this::scheduleView).toList());
  }

  private void createSchedule(Context ctx) {
    var payload = ctx.bodyAsClass(SchedulePayload.class);
    Principal principal = ctx.attribute("principal");
    var schedule =
        core.createSchedule(
            payload.automation(),
            payload.trigger(),
            payload.params() == null ? Map.of() : payload.params(),
            principal.subject());
    ctx.status(201).json(scheduleView(schedule));
  }

  private void setScheduleState(Context ctx, boolean enabled) {
    core.setScheduleEnabled(ctx.pathParam("id"), enabled);
    var schedule =
        core.schedules().stream()
            .filter(item -> item.id().equals(ctx.pathParam("id")))
            .findFirst()
            .orElseThrow();
    ctx.json(scheduleView(schedule));
  }

  private void deleteSchedule(Context ctx) {
    boolean deleted = core.deleteSchedule(ctx.pathParam("id"));
    ctx.json(Map.of("deleted", deleted));
  }

  private Map<String, Object> scheduleView(com.accord.sia.core.schedule.Schedule schedule) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("id", schedule.id());
    view.put("automation", schedule.target());
    view.put("trigger", schedule.trigger());
    view.put("enabled", schedule.enabled());
    view.put("nextFireAt", schedule.nextFireAt() == null ? null : instant(schedule.nextFireAt()));
    view.put("owner", schedule.owner());
    return view;
  }

  private void openapi(Context ctx) throws java.io.IOException {
    ctx.contentType("application/yaml");
    ctx.result(
        SiaServer.class.getResourceAsStream("/openapi.yaml") == null
            ? "openapi: 3.0.3"
            : new String(
                SiaServer.class.getResourceAsStream("/openapi.yaml").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8));
  }

  private static RunId runId(Context ctx) {
    return RunId.of(ctx.pathParam("id"));
  }

  private Map<String, Object> runView(RunRecord record) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("id", record.id().value());
    view.put("plugin", record.pluginId());
    view.put("automation", record.automationId());
    view.put("actor", record.actor());
    view.put("status", record.status().name());
    view.put("startedAt", instant(record.startedAt()));
    view.put("finishedAt", instant(record.finishedAt()));
    view.put("trigger", record.trigger());
    view.put("errorCode", record.errorCode());
    view.put("message", record.message());
    view.put("params", record.paramsKeys());
    view.put("outputs", record.outputsKeys());
    return view;
  }

  /** Instantes saem com o deslocamento do fuso configurado (Brasilia por padrao). */
  private String instant(Instant instant) {
    return instant == null ? null : java.time.OffsetDateTime.ofInstant(instant, zone).toString();
  }

  private static int parseInt(String value, int fallback) {
    try {
      return value == null ? fallback : Integer.parseInt(value);
    } catch (NumberFormatException error) {
      return fallback;
    }
  }

  /** Corpo do {@code POST /api/workflows/{name}/runs}. */
  public record WorkflowInputsPayload(Map<String, Object> inputs) {}

  /** Corpo do {@code POST /api/schedules}. */
  public record SchedulePayload(
      String automation,
      com.accord.sia.core.schedule.Trigger trigger,
      Map<String, String> params) {}

  /** Corpo dos endpoints de plugin. */
  public record PluginPathPayload(String path) {}

  /** Corpo do {@code POST /api/runs}. */
  public record RunRequestPayload(String automation, Map<String, Object> params) {
    static final String TRIGGER_API = "api";
  }
}
