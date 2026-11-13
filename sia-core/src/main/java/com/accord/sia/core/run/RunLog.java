package com.accord.sia.core.run;

import com.accord.sia.api.Log;
import com.accord.sia.api.RunId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Log de uma execucao: mascara valores sensiveis antes de gravar e de imprimir.
 *
 * <p>A redacao aqui nao confia no plugin: mesmo que o autor concatene o parametro na mensagem, o
 * valor marcado como sensivel vira {@code ***} no banco e no console.
 */
public final class RunLog implements Log {

  private final RunRepository repository;
  private final Redactor redactor;
  private final RunId runId;
  private final Logger logger;

  public RunLog(RunRepository repository, Redactor redactor, RunId runId, String pluginId) {
    this.repository = repository;
    this.redactor = redactor;
    this.runId = runId;
    this.logger = LoggerFactory.getLogger("sia.run." + pluginId);
  }

  @Override
  public void debug(String message) {
    write("DEBUG", message, null);
  }

  @Override
  public void info(String message) {
    write("INFO", message, null);
  }

  @Override
  public void warn(String message) {
    write("WARN", message, null);
  }

  @Override
  public void error(String message) {
    write("ERROR", message, null);
  }

  @Override
  public void error(String message, Throwable error) {
    write("ERROR", message + (error == null ? "" : " | " + error.getClass().getSimpleName()), null);
  }

  @Override
  public void event(String code, String message) {
    write("EVENT", message, code);
  }

  private void write(String level, String message, String eventCode) {
    String safe = redactor.redact(message == null ? "" : message);
    repository.appendLog(runId, level, eventCode, safe);
    MDC.put("runId", runId.value());
    try {
      switch (level) {
        case "ERROR" -> logger.error(safe);
        case "WARN" -> logger.warn(safe);
        case "DEBUG" -> logger.debug(safe);
        default -> logger.info(safe);
      }
    } finally {
      MDC.remove("runId");
    }
  }
}
