package com.accord.sia.api;

/** Falha ao carregar o plugin (JAR ilegivel, classe ausente, versao de API incompativel). */
public class PluginLoadException extends Exception {

  private final String pluginId;

  public PluginLoadException(String pluginId, String message, Throwable cause) {
    super("plugin " + pluginId + ": " + message, cause);
    this.pluginId = pluginId;
  }

  public PluginLoadException(String pluginId, String message) {
    this(pluginId, message, null);
  }

  public String pluginId() {
    return pluginId;
  }
}
