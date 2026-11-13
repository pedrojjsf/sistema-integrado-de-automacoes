package com.accord.sia.core.run;

import com.accord.sia.api.Automation;
import com.accord.sia.api.AutomationCancelledException;
import com.accord.sia.api.AutomationContext;
import com.accord.sia.api.AutomationDescriptor;
import com.accord.sia.api.AutomationException;
import com.accord.sia.api.AutomationResult;
import com.accord.sia.api.Parameters;
import com.accord.sia.api.PermissionDeniedException;
import com.accord.sia.api.RunId;
import com.accord.sia.core.plugin.PluginRuntime;
import com.accord.sia.core.plugin.PluginState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Executa automacoes com limite de tempo, cancelamento e historico.
 *
 * <p>Garantias:
 *
 * <ul>
 *   <li>uma execucao por vez nao derruba o daemon: bulkhead por plugin, com fila curta;
 *   <li>timeout cancela o token e interrompe a thread do plugin;
 *   <li>tudo que e gravado passa pelo {@link Redactor} dos parametros sensiveis;
 *   <li>o banco guarda nomes de parametro e chaves de saida, nunca valores.
 * </ul>
 */
public final class ExecutionEngine implements AutoCloseable {

  private static final String CONCURRENCY_LIMIT = "CONCURRENCY_LIMIT";

  private final RunRepository repository;
  private final Clock clock;
  private final int permitsPerPlugin;
  private final Duration queueWait;
  private final Duration defaultTimeout;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final Map<String, Semaphore> bulkheads = new ConcurrentHashMap<>();
  private final Map<String, DefaultCancellationToken> running = new ConcurrentHashMap<>();

  public ExecutionEngine(
      RunRepository repository,
      Clock clock,
      int permitsPerPlugin,
      Duration queueWait,
      Duration defaultTimeout) {
    this.repository = repository;
    this.clock = clock;
    this.permitsPerPlugin = Math.max(1, permitsPerPlugin);
    this.queueWait = queueWait == null ? Duration.ofSeconds(30) : queueWait;
    this.defaultTimeout = defaultTimeout == null ? Duration.ofMinutes(5) : defaultTimeout;
  }

  public RunOutcome execute(PluginRuntime plugin, RunRequest request) {
    if (plugin.state() != PluginState.ENABLED) {
      throw new IllegalStateException(
          "plugin " + plugin.id() + " nao esta ENABLED (estado: " + plugin.state() + ")");
    }
    Automation automation = findAutomation(plugin, request.automationId());
    AutomationDescriptor descriptor = automation.describe();
    Redactor redactor = redactorOf(descriptor, request.params());

    Semaphore bulkhead =
        bulkheads.computeIfAbsent(plugin.id(), key -> new Semaphore(permitsPerPlugin));
    if (!acquire(bulkhead)) {
      return reject(plugin, descriptor, request, redactor);
    }

    RunId runId =
        repository.start(
            plugin.id(),
            descriptor.id(),
            request.actor(),
            request.trigger(),
            request.params().names());
    Instant started = clock.instant();
    DefaultCancellationToken cancellation = new DefaultCancellationToken();
    running.put(runId.value(), cancellation);

    RunStatus status = RunStatus.FAILURE;
    String errorCode = null;
    String message = null;
    Map<String, Object> outputs = Map.of();
    Duration timeout = request.timeout() == null ? defaultTimeout : request.timeout();

    try {
      AutomationContext context =
          new AutomationContext(
              runId,
              plugin.id(),
              descriptor.id(),
              request.actor(),
              request.params(),
              new RunLog(repository, redactor, runId, plugin.id()),
              new RunProgress(repository, redactor, runId),
              cancellation,
              plugin.context().storage(),
              plugin.context().events(),
              plugin.context().secrets(),
              plugin.context().processes(),
              clock);
      AutomationResult result = invoke(automation, context, timeout, cancellation);
      status = mapStatus(result.status());
      errorCode = result.errorCode();
      message = result.message();
      outputs = result.outputs();
    } catch (RunTimedOut timedOut) {
      status = RunStatus.TIMED_OUT;
      errorCode = "RUN_TIMEOUT";
      message = "timeout de " + timeout.toSeconds() + "s";
    } catch (AutomationCancelledException cancelled) {
      status = RunStatus.CANCELLED;
      errorCode = "RUN_CANCELLED";
      message = "execucao cancelada";
    } catch (AutomationException failure) {
      status = RunStatus.FAILURE;
      errorCode = failure.errorCode();
      message = failure.getMessage();
    } catch (PermissionDeniedException denied) {
      status = RunStatus.FAILURE;
      errorCode = "PERMISSION_DENIED";
      message = denied.getMessage();
    } catch (RuntimeException unexpected) {
      status = RunStatus.FAILURE;
      errorCode = "UNEXPECTED_ERROR";
      message = describe(unexpected);
    } finally {
      running.remove(runId.value());
      bulkhead.release();
    }

    String safeMessage = redactor.redact(message);
    repository.finish(runId, status, errorCode, safeMessage, outputs.keySet());
    return new RunOutcome(
        runId, status, errorCode, safeMessage, outputs, Duration.between(started, clock.instant()));
  }

  /** Pede cancelamento de uma execucao em andamento. */
  public boolean cancel(RunId runId) {
    DefaultCancellationToken token = running.get(runId.value());
    if (token == null) {
      return false;
    }
    token.cancel();
    return true;
  }

  /** Cancela tudo (desligamento gracioso do daemon). */
  public int cancelAll() {
    List<DefaultCancellationToken> tokens = new ArrayList<>(running.values());
    tokens.forEach(DefaultCancellationToken::cancel);
    return tokens.size();
  }

  public int runningCount() {
    return running.size();
  }

  private Automation findAutomation(PluginRuntime plugin, String automationId) {
    return plugin.instance().automations().stream()
        .filter(automation -> automation.id().equals(automationId))
        .findFirst()
        .orElseThrow(() ->
            new IllegalArgumentException(
                "automacao nao encontrada: " + automationId + " em " + plugin.id()));
  }

  private AutomationResult invoke(
      Automation automation,
      AutomationContext context,
      Duration timeout,
      DefaultCancellationToken cancellation)
      throws AutomationException {
    Future<AutomationResult> future = executor.submit(() -> automation.execute(context));
    try {
      return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException timedOut) {
      cancellation.cancel();
      future.cancel(true);
      throw new RunTimedOut();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      cancellation.cancel();
      future.cancel(true);
      throw new AutomationCancelledException();
    } catch (ExecutionException failed) {
      Throwable cause = failed.getCause();
      if (cause instanceof AutomationException known) {
        throw known;
      }
      if (cause instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new IllegalStateException("falha inesperada na automacao", cause);
    }
  }

  private RunOutcome reject(
      PluginRuntime plugin,
      AutomationDescriptor descriptor,
      RunRequest request,
      Redactor redactor) {
    RunId runId =
        repository.start(
            plugin.id(),
            descriptor.id(),
            request.actor(),
            request.trigger(),
            request.params().names());
    String message = "limite de " + permitsPerPlugin + " execucoes simultaneas do plugin atingido";
    repository.finish(runId, RunStatus.REJECTED, CONCURRENCY_LIMIT, message, List.of());
    return new RunOutcome(
        runId,
        RunStatus.REJECTED,
        CONCURRENCY_LIMIT,
        redactor.redact(message),
        Map.of(),
        Duration.ZERO);
  }

  private static Redactor redactorOf(AutomationDescriptor descriptor, Parameters params) {
    List<String> sensitive = new ArrayList<>();
    for (var spec : descriptor.params()) {
      if (spec.sensitive()) {
        params.raw(spec.name()).ifPresent(value -> sensitive.add(String.valueOf(value)));
      }
    }
    return Redactor.of(sensitive);
  }

  private static RunStatus mapStatus(AutomationResult.Status status) {
    return switch (status) {
      case SUCCESS -> RunStatus.SUCCESS;
      case FAILURE -> RunStatus.FAILURE;
      case SKIPPED -> RunStatus.SKIPPED;
    };
  }

  private boolean acquire(Semaphore bulkhead) {
    try {
      return bulkhead.tryAcquire(queueWait.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private static String describe(Throwable error) {
    String detail = error.getMessage() == null ? "" : ": " + error.getMessage();
    String text = error.getClass().getSimpleName() + detail;
    return text.length() > 300 ? text.substring(0, 300) : text;
  }

  public Optional<RunOutcome> outcomeOf(RunId runId) {
    return repository
        .find(runId)
        .map(record ->
            new RunOutcome(
                record.id(),
                record.status(),
                record.errorCode(),
                record.message(),
                Map.of(),
                record.finishedAt() == null
                    ? Duration.ZERO
                    : Duration.between(record.startedAt(), record.finishedAt())));
  }

  @Override
  public void close() {
    cancelAll();
    executor.shutdown();
    try {
      if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      executor.shutdownNow();
    }
  }

  private static final class RunTimedOut extends RuntimeException {
    RunTimedOut() {
      super("timeout", null, false, false);
    }
  }
}
