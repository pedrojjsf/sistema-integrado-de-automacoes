package com.accord.sia.api;

/** Falha ao iniciar ou acompanhar um processo externo. */
public class ProcessException extends RuntimeException {

  public enum Reason {
    NOT_ALLOWED,
    NOT_FOUND,
    IO
  }

  private final Reason reason;

  public ProcessException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public ProcessException(Reason reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
