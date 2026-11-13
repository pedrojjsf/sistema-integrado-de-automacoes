package com.accord.sia.core.persistence;

/** Falha de acesso ao banco, ja convertida de {@code SQLException}. */
public class DataAccessException extends RuntimeException {

  public DataAccessException(String message, Throwable cause) {
    super(message, cause);
  }

  public DataAccessException(String message) {
    super(message);
  }
}
