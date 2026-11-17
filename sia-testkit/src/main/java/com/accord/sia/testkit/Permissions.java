package com.accord.sia.testkit;

import com.accord.sia.api.PluginPermission;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/** Permissoes concedidas ao plugin durante o teste. */
public record Permissions(Set<PluginPermission> granted) {

  public Permissions {
    granted = granted == null ? Set.of() : Set.copyOf(granted);
  }

  /** Padrao: plugin sem nenhuma permissao — o que nao foi concedido tem de falhar. */
  public static Permissions none() {
    return new Permissions(Set.of());
  }

  public static Permissions of(PluginPermission... permissions) {
    return new Permissions(
        permissions == null ? Set.of() : EnumSet.copyOf(Arrays.asList(permissions)));
  }

  public static Permissions all() {
    return new Permissions(EnumSet.allOf(PluginPermission.class));
  }

  public boolean allows(PluginPermission permission) {
    return granted.contains(permission);
  }
}
