package com.accord.sia.api;

import java.util.Optional;

/** Configuracao declarada pelo plugin (chaves simples, valores texto). */
public interface Configuration {

  Optional<String> get(String key);

  String get(String key, String fallback);

  /**
   * @throws ConfigurationException quando a chave obrigatoria nao existe
   */
  default String require(String key) {
    return get(key).orElseThrow(() -> new ConfigurationException("configuracao ausente: " + key));
  }
}
