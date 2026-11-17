package com.accord.sia.core.logging;

import com.accord.sia.api.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Log do plugin, com {@code pluginId} no MDC.
 *
 * <p>Nao existe metodo com varargs: a API so aceita mensagem pronta justamente para dificultar
 * colar valor de parametro no log. O engine complementa com redacao por valor.
 */
public final class Slf4jLog implements Log {

  private final Logger logger;
  private final String pluginId;

  public Slf4jLog(String pluginId) {
    this.pluginId = pluginId;
    this.logger = LoggerFactory.getLogger("sia.plugin." + pluginId);
  }

  @Override
  public void debug(String message) {
    withContext(() -> logger.debug(message));
  }

  @Override
  public void info(String message) {
    withContext(() -> logger.info(message));
  }

  @Override
  public void warn(String message) {
    withContext(() -> logger.warn(message));
  }

  @Override
  public void error(String message) {
    withContext(() -> logger.error(message));
  }

  @Override
  public void error(String message, Throwable error) {
    withContext(() -> logger.error(message, error));
  }

  @Override
  public void event(String code, String message) {
    withContext(() -> logger.info("[{}] {}", code, message));
  }

  private void withContext(Runnable action) {
    MDC.put("pluginId", pluginId);
    try {
      action.run();
    } finally {
      MDC.remove("pluginId");
    }
  }
}
