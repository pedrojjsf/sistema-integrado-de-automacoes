package com.accord.sia.core.run;

import com.accord.sia.api.RunId;
import java.time.Duration;
import java.util.Map;

/**
 * Resultado de uma execucao, para quem chamou.
 *
 * <p>{@code outputs} existe em memoria para encadear passos de workflow; o banco guarda apenas as
 * chaves.
 */
public record RunOutcome(
    RunId runId,
    RunStatus status,
    String errorCode,
    String message,
    Map<String, Object> outputs,
    Duration duration) {

  public RunOutcome {
    outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
  }

  public boolean succeeded() {
    return status == RunStatus.SUCCESS;
  }
}
