package com.accord.sia.core.permission;

import com.accord.sia.api.PermissionDeniedException;
import com.accord.sia.api.PluginPermission;
import com.accord.sia.core.process.PermissionCheck;
import java.util.Set;

/** Negacao por padrao: o que o manifesto nao declarou, o plugin nao usa. */
public final class PermissionGuard implements PermissionCheck {

  private final String pluginId;
  private final Set<PluginPermission> granted;

  public PermissionGuard(String pluginId, Set<PluginPermission> granted) {
    this.pluginId = pluginId;
    this.granted = granted == null ? Set.of() : Set.copyOf(granted);
  }

  @Override
  public void require(PluginPermission permission) {
    if (!granted.contains(permission)) {
      throw new PermissionDeniedException(permission.wireName(), pluginId);
    }
  }

  public Set<PluginPermission> granted() {
    return granted;
  }
}
