package com.accord.sia.auth;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Papeis do SIA e as permissoes que cada um concede.
 *
 * <p>A hierarquia e aditiva: OPERATOR contem tudo de VIEWER e ADMIN contem todas as permissoes
 * existentes. Um papel novo precisa declarar suas permissoes aqui - nao ha concessao implicita.
 */
public enum SiaRole {
  VIEWER,
  OPERATOR,
  ADMIN;

  private Set<Permission> permissions;

  static {
    VIEWER.permissions =
        Collections.unmodifiableSet(
            EnumSet.of(Permission.HEALTH_READ, Permission.PLUGIN_READ, Permission.RUN_READ));

    EnumSet<Permission> operator = EnumSet.copyOf(VIEWER.permissions);
    operator.add(Permission.RUN_EXECUTE);
    operator.add(Permission.RUN_CANCEL);
    OPERATOR.permissions = Collections.unmodifiableSet(operator);

    ADMIN.permissions = Collections.unmodifiableSet(EnumSet.allOf(Permission.class));
  }

  /** Permissoes concedidas por este papel (imutavel). */
  public Set<Permission> permissions() {
    return permissions;
  }

  public boolean implies(Permission permission) {
    return permission != null && permissions.contains(permission);
  }
}
