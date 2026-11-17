package com.accord.sia.api;

/** Configuracao ausente ou invalida para o plugin. */
public class ConfigurationException extends RuntimeException {

  public ConfigurationException(String message) {
    super(message);
  }
}
