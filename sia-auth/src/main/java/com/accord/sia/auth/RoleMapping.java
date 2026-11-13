package com.accord.sia.auth;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Traduz valores do claim de papeis (ex.: {@code "sia.operator"}) para {@link SiaRole}.
 *
 * <p>Valores desconhecidos sao ignorados de proposito: um claim adulterado ou de outro aplicativo
 * nao escala privilegio. Sem valor reconhecido, o resultado e conjunto vazio (deny-by-default).
 */
public final class RoleMapping {

  private static final RoleMapping APP_ROLES =
      new RoleMapping(
          Map.of(
              "sia.viewer", SiaRole.VIEWER,
              "sia.operator", SiaRole.OPERATOR,
              "sia.admin", SiaRole.ADMIN));

  private final Map<String, SiaRole> byClaim;

  private RoleMapping(Map<String, SiaRole> byClaim) {
    Map<String, SiaRole> normalized = new LinkedHashMap<>();
    byClaim.forEach((claim, role) -> normalized.put(claim.toLowerCase(Locale.ROOT), role));
    this.byClaim = Map.copyOf(normalized);
  }

  /** Mapa padrao dos App Roles registrados no IdP. */
  public static RoleMapping appRoles() {
    return APP_ROLES;
  }

  /** Mapa customizado (ex.: GUID de grupo do Entra ID para papel). */
  public static RoleMapping of(Map<String, SiaRole> claimToRole) {
    return new RoleMapping(claimToRole == null ? Map.of() : claimToRole);
  }

  public Set<SiaRole> map(Collection<String> claimValues) {
    if (claimValues == null || claimValues.isEmpty()) {
      return Set.of();
    }
    EnumSet<SiaRole> roles = EnumSet.noneOf(SiaRole.class);
    for (String value : claimValues) {
      if (value == null || value.isBlank()) {
        continue;
      }
      SiaRole role = byClaim.get(value.toLowerCase(Locale.ROOT));
      if (role != null) {
        roles.add(role);
      }
    }
    return Set.copyOf(roles);
  }
}
