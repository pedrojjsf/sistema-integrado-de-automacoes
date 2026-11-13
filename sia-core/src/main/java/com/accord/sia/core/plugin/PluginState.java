package com.accord.sia.core.plugin;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Estados do ciclo de vida de um plugin e as transicoes permitidas. */
public enum PluginState {
  DISCOVERED,
  VALIDATED,
  LOADED,
  INITIALIZED,
  ENABLED,
  DISABLED,
  UNLOADED,
  FAILED;

  private static final Map<PluginState, Set<PluginState>> ALLOWED =
      Map.of(
          DISCOVERED, EnumSet.of(VALIDATED, FAILED),
          VALIDATED, EnumSet.of(LOADED, FAILED),
          LOADED, EnumSet.of(INITIALIZED, UNLOADED, FAILED),
          INITIALIZED, EnumSet.of(ENABLED, UNLOADED, FAILED),
          ENABLED, EnumSet.of(DISABLED, UNLOADED, FAILED),
          DISABLED, EnumSet.of(ENABLED, UNLOADED),
          UNLOADED, EnumSet.noneOf(PluginState.class),
          FAILED, EnumSet.of(UNLOADED));

  public boolean canTransitionTo(PluginState next) {
    return next != null && ALLOWED.get(this).contains(next);
  }

  public boolean isTerminal() {
    return this == UNLOADED;
  }
}
