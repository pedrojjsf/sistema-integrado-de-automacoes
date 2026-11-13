package com.accord.sia.api;

/** Falha recuperavel de uma automacao. */
public class AutomationException extends Exception {

  private final String errorCode;

  public AutomationException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public AutomationException(String errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
  }

  public String errorCode() {
    return errorCode;
  }
}
