package com.accord.sia.testkit;

import com.accord.sia.api.RunId;
import com.accord.sia.core.run.RunStatus;
import java.util.List;
import java.util.Map;

/**
 * Resultado de uma execucao disparada pelo {@link PluginTestKit}.
 *
 * @param runId identificador
 * @param status desfecho
 * @param errorCode codigo curto quando falhou
 * @param message mensagem redigida
 * @param outputs chaves produzidas
 * @param logs linhas gravadas no log da execucao
 */
public record RunResult(
    RunId runId,
    RunStatus status,
    String errorCode,
    String message,
    Map<String, Object> outputs,
    List<String> logs) {

  public RunResult {
    outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
    logs = logs == null ? List.of() : List.copyOf(logs);
  }

  public boolean succeeded() {
    return status == RunStatus.SUCCESS;
  }

  /** Falha o teste com mensagem util quando a execucao nao terminou com sucesso. */
  public RunResult assertSuccess() {
    if (!succeeded()) {
      throw new AssertionError(
          "esperava SUCCESS, veio " + status + " (" + errorCode + ": " + message + ")");
    }
    return this;
  }

  public RunResult assertFailure(String expectedErrorCode) {
    if (succeeded()) {
      throw new AssertionError("esperava falha com " + expectedErrorCode + ", mas teve sucesso");
    }
    if (expectedErrorCode != null && !expectedErrorCode.equals(errorCode)) {
      throw new AssertionError(
          "esperava errorCode " + expectedErrorCode + ", veio " + errorCode + " (" + message + ")");
    }
    return this;
  }

  /** Verifica que nenhum dos valores aparece no log da execucao. */
  public RunResult assertNoSensitiveData(String... values) {
    for (String value : values) {
      if (value == null || value.length() < 3) {
        continue;
      }
      for (String line : logs) {
        if (line.contains(value)) {
          throw new AssertionError(
              "valor sensivel apareceu no log: " + value + " em '" + line + "'");
        }
      }
    }
    return this;
  }
}
