package com.accord.sia.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificador de uma execucao.
 *
 * @param value identificador opaco, unico no tempo
 */
public record RunId(String value) {

  public RunId {
    Objects.requireNonNull(value, "value");
    if (value.isBlank()) {
      throw new IllegalArgumentException("runId nao pode ser vazio");
    }
  }

  public static RunId generate() {
    return new RunId(UUID.randomUUID().toString());
  }

  public static RunId of(String value) {
    return new RunId(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
