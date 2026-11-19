package com.accord.sia.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gera {@code types/api.d.ts} a partir de {@code openapi.yaml}.
 *
 * <p>Assim o frontend e tipado pelo contrato da API: se um campo mudar no OpenAPI, o {@code tsc}
 * reclama no {@code app.js} antes de abrir o navegador. Sem Node no build — o gerador e Java.
 */
public final class OpenApiTypesGenerator {

  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

  private OpenApiTypesGenerator() {}

  public static String generate(Path openapi) throws IOException {
    Map<String, Object> root = YAML.readValue(openapi.toFile(), Map.class);
    Map<String, Object> components = map(root.get("components"));
    Map<String, Object> schemas = map(components.get("schemas"));

    StringBuilder out = new StringBuilder();
    out.append("/**\n")
        .append(" * Tipos da API do SIA, gerados de sia-server/src/main/resources/openapi.yaml.\n")
        .append(" * NAO EDITE A MAO: rode `mvnw -pl sia-server test -Dtest=ApiTypesUpToDateTest`\n")
        .append(" * (ou apague o arquivo e rode o teste) para regerar.\n")
        .append(" */\n\n");
    out.append("export type HttpMethod = \"GET\" | \"POST\" | \"DELETE\";\n\n");

    for (Map.Entry<String, Object> entry : schemas.entrySet()) {
      out.append(interfaceFor(entry.getKey(), map(entry.getValue())));
      out.append('\n');
    }
    return out.toString();
  }

  private static String interfaceFor(String name, Map<String, Object> schema) {
    StringBuilder out = new StringBuilder();
    Object enumValues = schema.get("enum");
    if (enumValues instanceof List<?> values) {
      out.append("export type ").append(name).append(" = ");
      List<String> literals = new ArrayList<>();
      for (Object value : values) {
        literals.add("\"" + value + "\"");
      }
      out.append(String.join(" | ", literals)).append(";\n");
      return out.toString();
    }

    List<String> required = strings(schema.get("required"));
    Map<String, Object> properties = map(schema.get("properties"));
    out.append("export interface ").append(name).append(" {\n");
    for (Map.Entry<String, Object> property : properties.entrySet()) {
      Map<String, Object> spec = map(property.getValue());
      boolean isRequired = required.contains(property.getKey());
      String type = typeOf(spec);
      out.append("  ")
          .append(property.getKey())
          .append(isRequired ? "" : "?")
          .append(": ")
          .append(type)
          .append(";\n");
    }
    out.append("}\n");
    return out.toString();
  }

  private static String typeOf(Map<String, Object> spec) {
    Object ref = spec.get("$ref");
    if (ref instanceof String reference) {
      String name = reference.substring(reference.lastIndexOf('/') + 1);
      return name + (isNullable(spec) ? " | null" : "");
    }
    Object enumValues = spec.get("enum");
    if (enumValues instanceof List<?> values) {
      List<String> literals = new ArrayList<>();
      for (Object value : values) {
        literals.add("\"" + value + "\"");
      }
      return String.join(" | ", literals) + (isNullable(spec) ? " | null" : "");
    }
    String type = String.valueOf(spec.getOrDefault("type", "object"));
    String base =
        switch (type) {
          case "string" -> "string";
          case "integer", "number" -> "number";
          case "boolean" -> "boolean";
          case "array" -> arrayType(spec);
          case "object" -> objectType(spec);
          default -> "unknown";
        };
    return base + (isNullable(spec) ? " | null" : "");
  }

  private static String arrayType(Map<String, Object> spec) {
    Map<String, Object> items = map(spec.get("items"));
    return items.isEmpty() ? "unknown[]" : typeOf(items) + "[]";
  }

  private static String objectType(Map<String, Object> spec) {
    Map<String, Object> additional = map(spec.get("additionalProperties"));
    Map<String, Object> properties = map(spec.get("properties"));
    if (!additional.isEmpty() && properties.isEmpty()) {
      return "Record<string, " + typeOf(additional) + ">";
    }
    if (!properties.isEmpty()) {
      Map<String, String> fields = new LinkedHashMap<>();
      for (Map.Entry<String, Object> property : properties.entrySet()) {
        fields.put(property.getKey(), typeOf(map(property.getValue())));
      }
      List<String> parts = new ArrayList<>();
      fields.forEach((key, value) -> parts.add(key + ": " + value));
      return "{ " + String.join("; ", parts) + " }";
    }
    return "Record<string, unknown>";
  }

  private static boolean isNullable(Map<String, Object> spec) {
    return Boolean.TRUE.equals(spec.get("nullable"));
  }

  private static List<String> strings(Object value) {
    if (value instanceof List<?> list) {
      List<String> result = new ArrayList<>();
      for (Object item : list) {
        result.add(String.valueOf(item));
      }
      return result;
    }
    return List.of();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
  }
}
