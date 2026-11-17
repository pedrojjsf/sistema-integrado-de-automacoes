package com.accord.sia.core.manifest;

import com.accord.sia.api.InvalidPluginException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Le e valida o {@code plugin.yml}.
 *
 * <p>Leitura estrita: campo desconhecido no YAML e erro, para que um typo nao vire configuracao
 * silenciosamente ignorada.
 */
public final class ManifestLoader {

  private final ObjectMapper mapper;
  private final ManifestValidator validator;

  public ManifestLoader() {
    this.mapper =
        new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    this.validator = new ManifestValidator();
  }

  public PluginManifest read(Path manifestFile) throws IOException, InvalidPluginException {
    return parse(Files.readString(manifestFile, StandardCharsets.UTF_8));
  }

  public PluginManifest parse(String yaml) throws InvalidPluginException {
    if (yaml == null || yaml.isBlank()) {
      throw new InvalidPluginException("plugin.yml", "arquivo vazio");
    }
    PluginManifest manifest;
    try {
      manifest = mapper.readValue(yaml, PluginManifest.class);
    } catch (JsonProcessingException error) {
      throw new InvalidPluginException("plugin.yml", error.getOriginalMessage());
    }
    validator.validate(manifest);
    return manifest;
  }
}
