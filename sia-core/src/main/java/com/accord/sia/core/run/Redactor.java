package com.accord.sia.core.run;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Mascara valores sensiveis em texto antes de gravar.
 *
 * <p>Trabalha por valor literal: o engine coleta os valores dos parametros marcados {@code
 * sensitive} e o mesmo texto — venha do plugin, da saida de um processo ou de um erro — passa por
 * aqui. Valores muito curtos sao ignorados para nao mascarar texto comum por acidente.
 */
public final class Redactor {

  static final String MASK = "***";
  private static final int MIN_LENGTH = 3;

  private final List<String> values;

  private Redactor(List<String> values) {
    this.values = values;
  }

  public static Redactor empty() {
    return new Redactor(List.of());
  }

  public static Redactor of(Collection<String> values) {
    if (values == null || values.isEmpty()) {
      return empty();
    }
    List<String> filtered = new ArrayList<>();
    for (String value : values) {
      if (value != null && value.length() >= MIN_LENGTH) {
        filtered.add(value);
      }
    }
    return new Redactor(List.copyOf(filtered));
  }

  public String redact(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    String result = text;
    for (String value : values) {
      result = result.replace(value, MASK);
    }
    return result;
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }
}
