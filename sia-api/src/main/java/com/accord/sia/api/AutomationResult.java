package com.accord.sia.api;

import java.util.Map;
import java.util.Objects;

/**
 * Resultado de uma execucao.
 *
 * <p>{@code outputs} existe para encadear passos de workflow em memoria. O Core persiste apenas as
 * <b>chaves</b> e, mesmo assim, somente quando a automacao declarar {@code persistOutputs}.
 *
 * @param status desfecho
 * @param outputs chaves produzidas (valores primitivos serializaveis)
 * @param errorCode codigo curto e estavel quando {@code status} e {@code FAILURE}
 * @param message mensagem sem dado sensivel
 */
public record AutomationResult(
    Status status, Map<String, Object> outputs, String errorCode, String message) {

  public enum Status {
    SUCCESS,
    FAILURE,
    SKIPPED
  }

  public AutomationResult {
    Objects.requireNonNull(status, "status");
    outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
    if (status == Status.FAILURE && (errorCode == null || errorCode.isBlank())) {
      throw new IllegalArgumentException("resultado FAILURE exige errorCode");
    }
    if (status != Status.FAILURE && errorCode != null) {
      throw new IllegalArgumentException("errorCode so faz sentido em FAILURE");
    }
  }

  public static AutomationResult success(Map<String, Object> outputs, String message) {
    return new AutomationResult(Status.SUCCESS, outputs, null, message);
  }

  public static AutomationResult success() {
    return new AutomationResult(Status.SUCCESS, Map.of(), null, null);
  }

  public static AutomationResult failure(String errorCode, String message) {
    return new AutomationResult(Status.FAILURE, Map.of(), errorCode, message);
  }

  public static AutomationResult skipped(String reason) {
    return new AutomationResult(Status.SKIPPED, Map.of(), null, reason);
  }

  public boolean isSuccess() {
    return status == Status.SUCCESS;
  }
}
