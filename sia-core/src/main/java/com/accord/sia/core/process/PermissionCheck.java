package com.accord.sia.core.process;

import com.accord.sia.api.PluginPermission;

/** Checagem de permissao do plugin dono do contexto. */
@FunctionalInterface
public interface PermissionCheck {

  /**
   * @throws com.accord.sia.api.PermissionDeniedException quando o plugin nao declarou a permissao
   */
  void require(PluginPermission permission);
}
