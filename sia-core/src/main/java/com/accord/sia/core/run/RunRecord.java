package com.accord.sia.core.run;

import com.accord.sia.api.RunId;
import java.time.Instant;
import java.util.List;

/** Linha da tabela {@code run}: quem, quando e o que — sem conteudo de parametro. */
public record RunRecord(
    RunId id,
    String pluginId,
    String automationId,
    String actor,
    String actorName,
    String onBehalfOf,
    RunStatus status,
    Instant startedAt,
    Instant finishedAt,
    String trigger,
    String errorCode,
    String message,
    List<String> paramsKeys,
    List<String> outputsKeys) {

  public RunRecord {
    paramsKeys = paramsKeys == null ? List.of() : List.copyOf(paramsKeys);
    outputsKeys = outputsKeys == null ? List.of() : List.copyOf(outputsKeys);
  }
}
