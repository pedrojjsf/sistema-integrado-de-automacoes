package com.accord.sia.auth;

/**
 * Falha de autorizacao: o servidor HTTP traduz {@link Reason#UNAUTHENTICATED} em 401 e {@link
 * Reason#FORBIDDEN} em 403.
 */
public final class AuthorizationException extends RuntimeException {

  public enum Reason {
    UNAUTHENTICATED,
    FORBIDDEN
  }

  private final Reason reason;

  public AuthorizationException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
