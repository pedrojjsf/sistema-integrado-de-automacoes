package com.accord.sia.api;

/** O plugin violou o contrato: metadados invalidos, id duplicado, parametro inconsistente. */
public class InvalidPluginException extends Exception {

  private final String field;

  public InvalidPluginException(String field, String message) {
    super(field + ": " + message);
    this.field = field;
  }

  public String field() {
    return field;
  }
}
