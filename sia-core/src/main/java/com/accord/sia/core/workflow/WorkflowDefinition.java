package com.accord.sia.core.workflow;

import java.util.List;
import java.util.Map;

/**
 * Workflow declarado em YAML.
 *
 * @param name nome do workflow
 * @param steps passos, em qualquer ordem (o DAG e derivado de {@code needs})
 * @param description resumo opcional
 */
public record WorkflowDefinition(String name, String description, List<WorkflowStep> steps) {

  public WorkflowDefinition {
    steps = steps == null ? List.of() : List.copyOf(steps);
  }

  public WorkflowStep step(String id) {
    return steps.stream()
        .filter(step -> step.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("passo desconhecido: " + id));
  }

  public Map<String, WorkflowStep> byId() {
    return steps.stream()
        .collect(
            java.util.stream.Collectors.toUnmodifiableMap(
                WorkflowStep::id, step -> step, (a, b) -> a));
  }
}
