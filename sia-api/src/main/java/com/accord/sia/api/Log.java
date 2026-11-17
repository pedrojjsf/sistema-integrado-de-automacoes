package com.accord.sia.api;

/**
 * Log escopado ao plugin/execucao.
 *
 * <p>Nao ha interpolacao com valores de parametro: o autor recebe metodos que aceitam apenas
 * mensagem, e o Core ainda redige valores marcados como sensiveis antes de escrever. Log nao e
 * lugar de payload.
 */
public interface Log {

  void debug(String message);

  void info(String message);

  void warn(String message);

  void error(String message);

  void error(String message, Throwable error);

  /** Evento estruturado e auditavel: codigo curto e mensagem sem dado pessoal. */
  void event(String code, String message);
}
