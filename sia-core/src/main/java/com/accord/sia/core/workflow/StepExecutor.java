package com.accord.sia.core.workflow;

import com.accord.sia.api.Parameters;
import java.util.Map;

/** Executa um passo do workflow (implementado pelo Core, sobre o ExecutionEngine). */
@FunctionalInterface
public interface StepExecutor {

  StepOutcome execute(String automationId, Parameters params);

  /**
   * Resultado de um passo.
   *
   * @param succeeded se o passo terminou com sucesso
   * @param errorCode codigo curto quando falhou
   * @param message mensagem sem dado sensivel
   * @param outputs chaves e valores usados pelos passos seguintes
   */
  record StepOutcome(
      boolean succeeded, String errorCode, String message, Map<String, Object> outputs) {

    public StepOutcome {
      outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
    }

    public static StepOutcome success(Map<String, Object> outputs) {
      return new StepOutcome(true, null, null, outputs);
    }

    public static StepOutcome failure(String errorCode, String message) {
      return new StepOutcome(false, errorCode, message, Map.of());
    }
  }
}
