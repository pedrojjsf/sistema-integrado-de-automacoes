package com.accord.sia.api;

/**
 * Identidade do plugin.
 *
 * @param id identificador unico, minusculo e com hifen (ex.: {@code email-automation})
 * @param name nome legivel
 * @param version versao do plugin em SemVer
 * @param apiVersion versao da API do SIA com que o plugin foi compilado
 * @param author quem mantem o plugin
 */
public record PluginMetadata(
    String id, String name, String version, String apiVersion, String author) {

  public PluginMetadata(String id, String name, String version, String apiVersion) {
    this(id, name, version, apiVersion, null);
  }
}
