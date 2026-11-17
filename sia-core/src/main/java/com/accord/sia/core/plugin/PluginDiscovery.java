package com.accord.sia.core.plugin;

import com.accord.sia.api.InvalidPluginException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Varre a pasta de plugins.
 *
 * <p>Regra de pacote: cada plugin e um diretorio com {@code plugin.yml} e exatamente um JAR. Pasta
 * sem manifesto e ignorada (sobra de copia, backup, lixo), pasta com manifesto e sem JAR e erro —
 * manifesto sozinho nao executa nada.
 */
public final class PluginDiscovery {

  public static final String MANIFEST_NAME = "plugin.yml";

  public List<PluginCandidate> discover(Path pluginsDirectory)
      throws IOException, InvalidPluginException {
    if (!Files.isDirectory(pluginsDirectory)) {
      return List.of();
    }
    List<PluginCandidate> candidates = new ArrayList<>();
    try (Stream<Path> children = Files.list(pluginsDirectory)) {
      for (Path child : children.sorted(Comparator.comparing(Path::toString)).toList()) {
        if (!Files.isDirectory(child)) {
          continue;
        }
        Path manifest = child.resolve(MANIFEST_NAME);
        if (!Files.isRegularFile(manifest)) {
          continue;
        }
        candidates.add(candidateOf(child, manifest));
      }
    }
    return List.copyOf(candidates);
  }

  private PluginCandidate candidateOf(Path directory, Path manifest)
      throws IOException, InvalidPluginException {
    List<Path> jars;
    try (Stream<Path> files = Files.list(directory)) {
      jars = files.filter(path -> path.getFileName().toString().endsWith(".jar")).toList();
    }
    if (jars.isEmpty()) {
      throw new InvalidPluginException(
          directory.getFileName() + "/" + MANIFEST_NAME, "manifesto sem JAR ao lado");
    }
    if (jars.size() > 1) {
      throw new InvalidPluginException(
          directory.getFileName() + "/" + MANIFEST_NAME,
          "pacote precisa ter um unico JAR, encontrados " + jars.size());
    }
    return new PluginCandidate(directory, manifest, jars.get(0));
  }
}
