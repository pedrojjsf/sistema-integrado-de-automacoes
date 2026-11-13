package com.accord.sia.core.plugin;

import com.accord.sia.api.InvalidPluginException;
import com.accord.sia.core.manifest.ManifestLoader;
import com.accord.sia.core.manifest.PluginManifest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Leitura do conteudo de um pacote de plugin (`plugin.yml` dentro do JAR). */
public final class PluginPackage {

  private PluginPackage() {}

  public static PluginManifest readManifest(java.nio.file.Path jar)
      throws IOException, InvalidPluginException {
    try (JarFile file = new JarFile(jar.toFile())) {
      JarEntry entry = file.getJarEntry("plugin.yml");
      if (entry == null) {
        throw new InvalidPluginException("plugin.yml", "nao encontrado dentro do JAR");
      }
      try (InputStream stream = file.getInputStream(entry)) {
        String yaml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        return new ManifestLoader().parse(yaml);
      }
    }
  }

  /** Verifica se a classe principal existe no JAR, sem carregar codigo do plugin. */
  public static boolean hasMainClass(java.nio.file.Path jar, String mainClass) throws IOException {
    if (mainClass == null || mainClass.isBlank()) {
      return false;
    }
    String entryName = mainClass.replace('.', '/') + ".class";
    try (JarFile file = new JarFile(jar.toFile())) {
      return file.getJarEntry(entryName) != null;
    }
  }

  public static String readEntry(java.nio.file.Path jar, String entryName) throws IOException {
    try (JarFile file = new JarFile(jar.toFile())) {
      JarEntry entry = file.getJarEntry(entryName);
      if (entry == null) {
        return "";
      }
      try (InputStream stream = file.getInputStream(entry)) {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
    }
  }
}
