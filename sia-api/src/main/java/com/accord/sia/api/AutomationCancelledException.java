package com.accord.sia.api;

/** Sinaliza que a execucao foi cancelada; nao e falha do plugin. */
public class AutomationCancelledException extends RuntimeException {

  public AutomationCancelledException() {
    super("execucao cancelada");
  }
}
