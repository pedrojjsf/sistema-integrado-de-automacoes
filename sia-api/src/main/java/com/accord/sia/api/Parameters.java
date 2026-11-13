package com.accord.sia.api;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Parametros de uma execucao, imutaveis e tipados.
 *
 * <p>Acesso tipado e estrito: pedir texto de um inteiro falha com mensagem explicita em vez de
 * converter silenciosamente, o que evita bug de producao silencioso em automacao.
 */
public final class Parameters {

  private static final Parameters EMPTY = new Parameters(Map.of());

  private final Map<String, Object> values;

  private Parameters(Map<String, Object> values) {
    this.values = Map.copyOf(values);
  }

  public static Parameters empty() {
    return EMPTY;
  }

  public static Parameters of(String name, Object value) {
    Map<String, Object> single = new LinkedHashMap<>();
    single.put(name, value);
    return new Parameters(single);
  }

  public static Parameters of(Map<String, Object> values) {
    return values == null || values.isEmpty() ? EMPTY : new Parameters(values);
  }

  public Set<String> names() {
    return values.keySet();
  }

  public boolean has(String name) {
    return values.containsKey(name);
  }

  public Map<String, Object> asMap() {
    return values;
  }

  public Optional<Object> raw(String name) {
    return Optional.ofNullable(values.get(name));
  }

  public Optional<String> string(String name) {
    Object value = values.get(name);
    if (value == null) {
      return Optional.empty();
    }
    if (value instanceof String text) {
      return Optional.of(text);
    }
    throw wrongType(name, "texto", value);
  }

  public String string(String name, String fallback) {
    return string(name).orElse(fallback);
  }

  public String requireString(String name) {
    return string(name)
        .orElseThrow(() -> new IllegalArgumentException("parametro ausente: " + name));
  }

  public int integer(String name, int fallback) {
    Object value = values.get(name);
    if (value == null) {
      return fallback;
    }
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value instanceof String text) {
      try {
        return Integer.parseInt(text.trim());
      } catch (NumberFormatException error) {
        throw wrongType(name, "inteiro", value);
      }
    }
    throw wrongType(name, "inteiro", value);
  }

  public boolean bool(String name, boolean fallback) {
    Object value = values.get(name);
    if (value == null) {
      return fallback;
    }
    if (value instanceof Boolean flag) {
      return flag;
    }
    if (value instanceof String text) {
      String normalized = text.trim().toLowerCase(java.util.Locale.ROOT);
      if ("true".equals(normalized)) {
        return true;
      }
      if ("false".equals(normalized)) {
        return false;
      }
    }
    throw wrongType(name, "booleano", value);
  }

  public BigDecimal decimal(String name) {
    Object value = values.get(name);
    if (value == null) {
      return null;
    }
    if (value instanceof BigDecimal decimal) {
      return decimal;
    }
    if (value instanceof Number number) {
      return BigDecimal.valueOf(number.doubleValue());
    }
    if (value instanceof String text) {
      try {
        return new BigDecimal(text.trim());
      } catch (NumberFormatException error) {
        throw wrongType(name, "decimal", value);
      }
    }
    throw wrongType(name, "decimal", value);
  }

  /** Copia sem os parametros indicados - util para tirar segredos antes de logar. */
  public Parameters without(String... names) {
    Map<String, Object> copy = new LinkedHashMap<>(values);
    for (String name : names) {
      copy.remove(name);
    }
    return new Parameters(copy);
  }

  public Parameters merge(Parameters other) {
    Map<String, Object> copy = new LinkedHashMap<>(values);
    copy.putAll(other.values);
    return new Parameters(copy);
  }

  private static IllegalArgumentException wrongType(String name, String expected, Object actual) {
    return new IllegalArgumentException(
        "parametro '"
            + name
            + "' deveria ser "
            + expected
            + " mas veio "
            + actual.getClass().getSimpleName());
  }

  @Override
  public String toString() {
    return "Parameters" + values.keySet();
  }
}
