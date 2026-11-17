package com.accord.sia.core.run;

import java.time.Instant;

/**
 * Evento de uma execucao em andamento, para streaming (SSE no daemon).
 *
 * @param runId execucao
 * @param type {@code log}, {@code progress}, {@code step} ou {@code finished}
 * @param level nivel do log, quando aplicavel
 * @param message mensagem ja redigida
 * @param at instante do evento
 */
public record RunEvent(String runId, String type, String level, String message, Instant at) {

  public static RunEvent log(String runId, String level, String message, Instant at) {
    return new RunEvent(runId, "log", level, message, at);
  }

  public static RunEvent progress(String runId, String message, Instant at) {
    return new RunEvent(runId, "progress", null, message, at);
  }

  public static RunEvent step(String runId, String message, Instant at) {
    return new RunEvent(runId, "step", null, message, at);
  }

  public static RunEvent finished(String runId, String status, Instant at) {
    return new RunEvent(runId, "finished", status, status, at);
  }
}
