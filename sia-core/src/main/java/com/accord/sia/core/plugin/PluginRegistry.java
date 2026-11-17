package com.accord.sia.core.plugin;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Registro de plugins carregados, por id, com deteccao de duplicata. */
public final class PluginRegistry {

  private final Map<String, LoadedPlugin> plugins = new ConcurrentHashMap<>();

  /**
   * @throws IllegalStateException se ja existe plugin com o mesmo id
   */
  public void register(LoadedPlugin plugin) {
    LoadedPlugin previous = plugins.putIfAbsent(plugin.id(), plugin);
    if (previous != null) {
      plugin.unload();
      throw new IllegalStateException(
          "plugin duplicado: " + plugin.id() + " ja carregado de " + previous.manifest().id());
    }
  }

  public Optional<LoadedPlugin> remove(String id) {
    return Optional.ofNullable(plugins.remove(id));
  }

  public Optional<LoadedPlugin> find(String id) {
    return Optional.ofNullable(plugins.get(id));
  }

  public Collection<LoadedPlugin> all() {
    return plugins.values().stream()
        .sorted(java.util.Comparator.comparing(LoadedPlugin::id))
        .toList();
  }

  public int size() {
    return plugins.size();
  }

  /** Descarrega todos, do ultimo para o primeiro, sem parar na primeira falha. */
  public void unloadAll() {
    for (LoadedPlugin plugin : all()) {
      try {
        plugin.unload();
      } catch (RuntimeException ignored) {
        // um plugin problematico nao impede os outros de fechar
      }
    }
    plugins.clear();
  }
}
