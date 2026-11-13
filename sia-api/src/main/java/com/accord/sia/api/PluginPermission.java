package com.accord.sia.api;

import java.util.Locale;

/**
 * Recursos do Core que um plugin pode pedir no manifesto.
 *
 * <p>Deny-by-default: o que nao esta declarado aqui nao pode ser usado, e o Core rejeita no
 * carregamento qualquer uso nao declarado.
 */
public enum PluginPermission {
  NETWORK,
  STORAGE,
  SECRETS,
  PROCESS,
  EVENTS;

  /** Nome usado no {@code plugin.yml} (minusculo). */
  public String wireName() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static PluginPermission fromWireName(String value) {
    if (value == null) {
      throw new IllegalArgumentException("permissao nula");
    }
    return switch (value.trim().toLowerCase(Locale.ROOT)) {
      case "network" -> NETWORK;
      case "storage" -> STORAGE;
      case "secrets" -> SECRETS;
      case "process" -> PROCESS;
      case "events" -> EVENTS;
      default -> throw new IllegalArgumentException("permissao desconhecida: " + value);
    };
  }
}
