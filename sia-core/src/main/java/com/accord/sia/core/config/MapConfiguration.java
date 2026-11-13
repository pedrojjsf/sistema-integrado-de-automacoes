package com.accord.sia.core.config;

import com.accord.sia.api.Configuration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Configuracao do plugin vinda do {@code config.yml} (chaves simples, valores texto).
 *
 * <p>Chaves sao case-insensitive e aceitam {@code a.b} e {@code a-b} como a mesma entrada, porque
 * quem escreve YAML nao deveria precisar memorizar o separador do codigo.
 */
public final class MapConfiguration implements Configuration {

  private final Map<String, String> values;

  public MapConfiguration(Map<String, String> values) {
    Map<String, String> normalized = new LinkedHashMap<>();
    if (values != null) {
      values.forEach((key, value) -> normalized.put(normalize(key), value));
    }
    this.values = Map.copyOf(normalized);
  }

  public static MapConfiguration empty() {
    return new MapConfiguration(Map.of());
  }

  @Override
  public Optional<String> get(String key) {
    return Optional.ofNullable(values.get(normalize(key)));
  }

  @Override
  public String get(String key, String fallback) {
    return get(key).orElse(fallback);
  }

  private static String normalize(String key) {
    return key == null ? "" : key.trim().toLowerCase(Locale.ROOT).replace('-', '.');
  }
}
