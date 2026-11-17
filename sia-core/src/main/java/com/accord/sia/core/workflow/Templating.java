package com.accord.sia.core.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolve {@code ${inputs.x}} e {@code ${steps.s.outputs.k}} nos parametros de um passo. */
final class Templating {

  private static final Pattern TOKEN =
      Pattern.compile("\\$\\{(inputs|steps)\\.([A-Za-z0-9_-]+)(?:\\.outputs\\.([A-Za-z0-9_-]+))?}");

  private Templating() {}

  static Map<String, Object> resolve(
      Map<String, Object> template,
      Map<String, Object> inputs,
      Map<String, Map<String, Object>> stepOutputs) {
    Map<String, Object> resolved = new java.util.LinkedHashMap<>();
    template.forEach((key, value) ->
        resolved.put(
            key, value instanceof String text ? resolveText(text, inputs, stepOutputs) : value));
    return Map.copyOf(resolved);
  }

  static String resolveText(
      String text, Map<String, Object> inputs, Map<String, Map<String, Object>> stepOutputs) {
    Matcher matcher = TOKEN.matcher(text);
    StringBuilder buffer = new StringBuilder();
    while (matcher.find()) {
      String source = matcher.group(1);
      String name = matcher.group(2);
      String key = matcher.group(3);
      Object value;
      if ("inputs".equals(source)) {
        value = inputs.get(name);
      } else {
        Map<String, Object> outputs = stepOutputs.getOrDefault(name, Map.of());
        value = key == null ? null : outputs.get(key);
      }
      matcher.appendReplacement(
          buffer, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
    }
    matcher.appendTail(buffer);
    return buffer.toString();
  }

  /** Lista de tokens de saida usados por um passo; util para diagnostico. */
  static List<String> references(String text) {
    List<String> references = new ArrayList<>();
    Matcher matcher = TOKEN.matcher(text);
    while (matcher.find()) {
      references.add(matcher.group(2));
    }
    return List.copyOf(references);
  }
}
