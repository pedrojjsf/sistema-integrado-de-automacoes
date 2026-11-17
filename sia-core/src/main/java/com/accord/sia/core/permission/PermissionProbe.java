package com.accord.sia.core.permission;

import com.accord.sia.api.PluginPermission;
import com.accord.sia.core.process.PermissionCheck;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Modo validacao: permite tudo e registra o que o plugin tentou usar.
 *
 * <p>Serve para o {@code plugin validate} apontar "este plugin usa {@code network} mas nao
 * declarou" antes de o plugin rodar em producao.
 */
public final class PermissionProbe implements PermissionCheck {

  private final Map<PluginPermission, Long> attempts = new ConcurrentHashMap<>();

  @Override
  public void require(PluginPermission permission) {
    attempts.merge(permission, 1L, Long::sum);
  }

  public Set<PluginPermission> used() {
    return Set.copyOf(attempts.keySet());
  }

  public long count(PluginPermission permission) {
    return attempts.getOrDefault(permission, 0L);
  }

  public void reset() {
    attempts.clear();
  }
}
