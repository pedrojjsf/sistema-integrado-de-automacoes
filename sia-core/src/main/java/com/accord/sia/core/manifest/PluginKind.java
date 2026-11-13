package com.accord.sia.core.manifest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Como o Core executa o plugin. */
public enum PluginKind {

  /** JAR Java que depende de {@code sia-api} e roda no processo do daemon. */
  JAVA,

  /** Executavel proprio, iniciado pelo Core e falado por JSON Lines em stdin/stdout. */
  PROCESS;

  @JsonValue
  public String wireName() {
    return name().toLowerCase(Locale.ROOT);
  }

  @JsonCreator
  public static PluginKind fromWireName(String value) {
    if (value == null) {
      throw new IllegalArgumentException("kind nulo");
    }
    return switch (value.trim().toLowerCase(Locale.ROOT)) {
      case "java" -> JAVA;
      case "process" -> PROCESS;
      default -> throw new IllegalArgumentException("kind desconhecido: " + value);
    };
  }
}
