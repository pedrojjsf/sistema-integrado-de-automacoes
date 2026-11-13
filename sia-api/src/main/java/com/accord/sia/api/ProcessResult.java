package com.accord.sia.api;

import java.time.Duration;

/**
 * Saida de um processo externo.
 *
 * @param exitCode codigo de saida; {@code -1} quando o processo foi morto por timeout
 * @param stdout saida padrao capturada (limitada e redigida)
 * @param stderr saida de erro capturada (limitada e redigida)
 * @param duration tempo total
 * @param timedOut se o processo foi encerrado pelo limite de tempo
 */
public record ProcessResult(
    int exitCode, String stdout, String stderr, Duration duration, boolean timedOut) {

  public boolean succeeded() {
    return !timedOut && exitCode == 0;
  }
}
