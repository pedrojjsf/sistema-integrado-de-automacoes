package com.accord.sia.core.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Le o {@code config.yml} com leitura estrita: chave desconhecida e erro, para um typo nao virar
 * configuracao silenciosamente ignorada.
 */
public final class ConfigLoader {

  private final ObjectMapper mapper =
      new ObjectMapper(new YAMLFactory())
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

  public SiaConfig read(Path file) throws IOException {
    if (!Files.isRegularFile(file)) {
      throw new IOException("configuracao nao encontrada: " + file.toAbsolutePath());
    }
    return parse(Files.readString(file, StandardCharsets.UTF_8));
  }

  public SiaConfig parse(String yaml) throws IOException {
    if (yaml == null || yaml.isBlank()) {
      return SiaConfig.defaults();
    }
    return mapper.readValue(yaml, SiaConfig.class);
  }
}
