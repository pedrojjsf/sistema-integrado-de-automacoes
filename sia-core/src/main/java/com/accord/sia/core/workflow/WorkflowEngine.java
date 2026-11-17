package com.accord.sia.core.workflow;

import com.accord.sia.api.Parameters;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Executa um workflow em DAG.
 *
 * <p>Passos sem dependencia entre si rodam em paralelo; um passo so comeca quando todos os {@code
 * needs} terminaram com sucesso. {@code onError = FAIL} interrompe o workflow, {@code CONTINUE}
 * segue em frente e {@code RETRY} tenta de novo antes de falhar.
 *
 * <p>Retomada: nos concluidos ficam gravados em {@code workflow_node}, e um novo {@link #resume}
 * pula o que ja foi feito — nao rebaixa anexo nem reenvia e-mail.
 */
public final class WorkflowEngine implements AutoCloseable {

  private final WorkflowRepository repository;
  private final StepExecutor executor;
  private final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();

  public WorkflowEngine(WorkflowRepository repository, StepExecutor executor) {
    this.repository = repository;
    this.executor = executor;
  }

  public WorkflowRun run(
      WorkflowDefinition definition, String definitionYaml, Map<String, Object> inputs) {
    String runId = UUID.randomUUID().toString();
    repository.createRun(runId, definition.name(), definitionYaml, WorkflowRepository.json(inputs));
    return execute(runId, definition, inputs, Map.of());
  }

  public WorkflowRun resume(String runId) throws IOException {
    WorkflowRepository.WorkflowRun stored =
        repository
            .findRun(runId)
            .orElseThrow(() -> new IllegalArgumentException("workflow desconhecido: " + runId));
    if (stored.definitionYaml() == null) {
      throw new IllegalStateException("execucao sem definicao para retomar: " + runId);
    }
    WorkflowDefinition definition = new WorkflowLoader().parse(stored.definitionYaml());
    return execute(runId, definition, stored.inputs(), repository.nodes(runId));
  }

  public WorkflowRun load(String runId) {
    WorkflowRepository.WorkflowRun stored =
        repository
            .findRun(runId)
            .orElseThrow(() -> new IllegalArgumentException("workflow desconhecido: " + runId));
    return new WorkflowRun(stored.id(), stored.name(), stored.status(), stored.nodeStatuses());
  }

  public List<WorkflowRun> recent(int limit) {
    return repository.recent(limit).stream()
        .map(run -> new WorkflowRun(run.id(), run.name(), run.status(), run.nodeStatuses()))
        .toList();
  }

  private WorkflowRun execute(
      String runId,
      WorkflowDefinition definition,
      Map<String, Object> inputs,
      Map<String, WorkflowRepository.NodeState> previous) {
    Map<String, String> status = new LinkedHashMap<>();
    Map<String, Map<String, Object>> outputs = new LinkedHashMap<>();
    for (WorkflowStep step : definition.steps()) {
      WorkflowRepository.NodeState prior = previous.get(step.id());
      if (prior != null && "DONE".equals(prior.status())) {
        status.put(step.id(), "DONE");
        outputs.put(step.id(), prior.outputs());
      } else {
        status.put(step.id(), "PENDING");
      }
    }

    boolean aborted = false;
    while (!aborted) {
      List<WorkflowStep> ready =
          definition.steps().stream()
              .filter(step -> "PENDING".equals(status.get(step.id())))
              .filter(
                  step -> step.needs().stream().allMatch(need -> "DONE".equals(status.get(need))))
              .toList();
      if (ready.isEmpty()) {
        break;
      }
      Map<String, StepExecutor.StepOutcome> wave = runWave(runId, ready, inputs, outputs);
      for (WorkflowStep step : ready) {
        StepExecutor.StepOutcome outcome = wave.get(step.id());
        if (outcome != null && outcome.succeeded()) {
          status.put(step.id(), "DONE");
          outputs.put(step.id(), outcome.outputs());
        } else {
          status.put(step.id(), "FAILED");
          if (step.onError() == WorkflowStep.OnError.FAIL) {
            aborted = true;
            break;
          }
        }
      }
    }

    status.replaceAll((node, state) -> "PENDING".equals(state) ? "SKIPPED" : state);
    String runStatus = overallStatus(status, aborted);
    repository.finishRun(runId, runStatus);
    return new WorkflowRun(runId, definition.name(), runStatus, Map.copyOf(status));
  }

  private Map<String, StepExecutor.StepOutcome> runWave(
      String runId,
      List<WorkflowStep> ready,
      Map<String, Object> inputs,
      Map<String, Map<String, Object>> outputs) {
    Map<String, Future<StepExecutor.StepOutcome>> futures = new LinkedHashMap<>();
    for (WorkflowStep step : ready) {
      futures.put(step.id(), pool.submit(() -> executeStep(runId, step, inputs, outputs)));
    }
    Map<String, StepExecutor.StepOutcome> results = new LinkedHashMap<>();
    futures.forEach((id, future) -> {
      try {
        results.put(id, future.get());
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        results.put(id, StepExecutor.StepOutcome.failure("INTERRUPTED", "execucao interrompida"));
      } catch (ExecutionException failed) {
        Throwable cause = failed.getCause() == null ? failed : failed.getCause();
        results.put(
            id,
            StepExecutor.StepOutcome.failure("UNEXPECTED_ERROR", cause.getClass().getSimpleName()));
      }
    });
    return results;
  }

  private StepExecutor.StepOutcome executeStep(
      String runId,
      WorkflowStep step,
      Map<String, Object> inputs,
      Map<String, Map<String, Object>> outputs) {
    Map<String, Object> params = Templating.resolve(step.with(), inputs, outputs);
    int attempts = step.onError() == WorkflowStep.OnError.RETRY ? step.retries() + 1 : 1;
    StepExecutor.StepOutcome outcome = null;
    for (int attempt = 1; attempt <= attempts; attempt++) {
      outcome = attemptStep(runId, step, params);
      if (outcome.succeeded()) {
        return outcome;
      }
    }
    return outcome;
  }

  private StepExecutor.StepOutcome attemptStep(
      String runId, WorkflowStep step, Map<String, Object> params) {
    repository.startNode(runId, step.id());
    try {
      StepExecutor.StepOutcome outcome = executor.execute(step.use(), Parameters.of(params));
      repository.finishNode(
          runId, step.id(), outcome.succeeded() ? "DONE" : "FAILED", outcome.outputs());
      return outcome;
    } catch (RuntimeException error) {
      repository.finishNode(runId, step.id(), "FAILED", Map.of());
      return StepExecutor.StepOutcome.failure("UNEXPECTED_ERROR", error.getMessage());
    }
  }

  /**
   * {@code FAIL} interrompe o workflow e o resultado e FAILURE. Falha tolerada por {@code CONTINUE}
   * ou passo pulado por dependencia quebrada deixa o resultado PARTIAL.
   */
  private static String overallStatus(Map<String, String> status, boolean aborted) {
    if (aborted) {
      return "FAILURE";
    }
    boolean anyProblem = status.containsValue("FAILED") || status.containsValue("SKIPPED");
    return anyProblem ? "PARTIAL" : "SUCCESS";
  }

  /** Resultado de uma execucao. */
  public record WorkflowRun(
      String id, String name, String status, Map<String, String> nodeStatuses) {

    public WorkflowRun {
      nodeStatuses = nodeStatuses == null ? Map.of() : Map.copyOf(nodeStatuses);
    }

    public List<String> nodes() {
      return new ArrayList<>(nodeStatuses.keySet());
    }
  }

  @Override
  public void close() {
    pool.shutdownNow();
  }
}
