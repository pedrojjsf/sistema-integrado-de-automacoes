package com.accord.sia.core.run;

/** Desfecho de uma execucao registrada. */
public enum RunStatus {
  RUNNING,
  SUCCESS,
  FAILURE,
  TIMED_OUT,
  CANCELLED,
  INTERRUPTED,
  REJECTED,
  SKIPPED;

  public static RunStatus fromWire(String value) {
    for (RunStatus status : values()) {
      if (status.name().equalsIgnoreCase(value)) {
        return status;
      }
    }
    throw new IllegalArgumentException("status desconhecido: " + value);
  }

  public boolean isFailure() {
    return switch (this) {
      case FAILURE, TIMED_OUT, CANCELLED, INTERRUPTED, REJECTED -> true;
      default -> false;
    };
  }

  public boolean isFinished() {
    return this != RUNNING;
  }
}
