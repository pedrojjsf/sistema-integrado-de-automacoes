package com.accord.sia.core.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Interface visual própria do plugin, servida pelo daemon.
 *
 * <p>O plugin declara {@code ui.entry} no manifesto e embarca os arquivos no próprio pacote ({@code
 * ui/panel.html}, {@code ui/painel.js}…). O console abre essa página num painel isolado; o plugin
 * nunca recebe HTML do Core e o Core nunca executa o JavaScript do plugin fora do iframe.
 *
 * <p>A leitura é restrita à pasta declarada: pedir {@code ../pom.xml} ou qualquer caminho fora de
 * {@code ui/} falha antes de tocar no disco.
 */
public final class PluginUi {

  private static final long MAX_BYTES = 5L * 1024 * 1024;

  private PluginUi() {}

  /** Conteúdo e tipo pronto para a resposta HTTP. */
  public record Resource(byte[] content, String contentType) {}

  public static Resource read(LoadedPlugin plugin, String requested) throws IOException {
    if (plugin.manifest().ui() == null) {
      throw new IOException("plugin " + plugin.id() + " nao declara interface propria");
    }
    String base = plugin.manifest().ui().entry();
    String folder = base.contains("/") ? base.substring(0, base.lastIndexOf('/') + 1) : "";
    String path = normalize(plugin, requested, folder);

    Path jar = plugin.directory() == null ? null : plugin.directory().resolve("plugin.jar");
    if (jar != null && Files.isRegularFile(jar)) {
      return fromJar(jar, path);
    }
    if (plugin.directory() != null) {
      Path file = plugin.directory().resolve(path).normalize();
      if (file.startsWith(plugin.directory().normalize()) && Files.isRegularFile(file)) {
        return new Resource(Files.readAllBytes(file), contentType(path));
      }
    }
    throw new IOException("recurso de interface nao encontrado: " + path);
  }

  private static String normalize(LoadedPlugin plugin, String requested, String folder)
      throws IOException {
    if (requested == null || requested.isBlank()) {
      throw new IOException("caminho de interface vazio");
    }
    String path = requested.replace('\\', '/').trim();
    if (path.startsWith("/") || path.contains("..") || path.contains(":") || path.contains("//")) {
      throw new IOException("caminho de interface invalido: " + requested);
    }
    if (!folder.isEmpty() && !path.startsWith(folder) && !path.equals(folder + "index.html")) {
      throw new IOException(
          "plugin "
              + plugin.id()
              + " so pode servir arquivos de "
              + folder
              + " (pedido: "
              + path
              + ")");
    }
    return path;
  }

  private static Resource fromJar(Path jar, String path) throws IOException {
    try (JarFile file = new JarFile(jar.toFile())) {
      JarEntry entry = file.getJarEntry(path);
      if (entry == null || entry.isDirectory()) {
        throw new IOException("recurso de interface nao encontrado no pacote: " + path);
      }
      if (entry.getSize() > MAX_BYTES) {
        throw new IOException("recurso de interface grande demais: " + path);
      }
      try (InputStream input = file.getInputStream(entry)) {
        byte[] content = input.readNBytes((int) MAX_BYTES);
        return new Resource(content, contentType(path));
      }
    }
  }

  static String contentType(String path) {
    String lower = path.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".html") || lower.endsWith(".htm")) {
      return "text/html; charset=utf-8";
    }
    if (lower.endsWith(".css")) {
      return "text/css; charset=utf-8";
    }
    if (lower.endsWith(".js") || lower.endsWith(".mjs")) {
      return "text/javascript; charset=utf-8";
    }
    if (lower.endsWith(".json")) {
      return "application/json; charset=utf-8";
    }
    if (lower.endsWith(".svg")) {
      return "image/svg+xml";
    }
    if (lower.endsWith(".png")) {
      return "image/png";
    }
    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
      return "image/jpeg";
    }
    if (lower.endsWith(".woff2")) {
      return "font/woff2";
    }
    return "application/octet-stream";
  }
}
