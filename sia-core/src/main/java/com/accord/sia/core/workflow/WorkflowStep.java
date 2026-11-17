package com.accord.sia.core.workflow;

import java.util.List;
import java.util.Map;

/**
 * Passo de um workflow.
 *
 * @param id identificador unico dentro do workflow
 * @param use automacao no formato {@code plugin.acao}
 * @param with parametros; valores texto aceitam {@code ${inputs.x}} e {@code ${steps.s.outputs.k}}
 * @param needs ids dos passos que precisam terminar antes
 * @param onError o que fazer quando este passo falha
 * @param retries tentativas extras quando {@code onError = RETRY}
 */
public record WorkflowStep(
    String id,
    String use,
    Map<String, Object> with,
    List<String> needs,
    OnError onError,
    int retries) {

  public enum OnError {
    FAIL,
    CONTINUE,
    RETRY
  }

  public WorkflowStep {
    needs = needs == null ? List.of() : List.copyOf(needs);
    with = with == null ? Map.of() : Map.copyOf(with);
    onError = onError == null ? OnError.FAIL : onError;
    retries = Math.max(0, retries);
  }
}
