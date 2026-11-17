package com.accord.sia.auth;

import java.util.Objects;

/**
 * Ponto unico de decisao de acesso.
 *
 * <p>Toda rota do daemon passa por aqui. Negacao e sempre explicita e o padrao: sem identidade,
 * 401; com identidade sem a permissao exigida, 403.
 */
public final class Authorizer {

  public void require(Principal principal, Permission permission) {
    Objects.requireNonNull(permission, "permission");
    if (principal == null) {
      throw new AuthorizationException(
          AuthorizationException.Reason.UNAUTHENTICATED, "requisicao sem identidade");
    }
    if (!principal.can(permission)) {
      throw new AuthorizationException(
          AuthorizationException.Reason.FORBIDDEN,
          "papeis " + principal.roles() + " nao concedem " + permission);
    }
  }

  public boolean allows(Principal principal, Permission permission) {
    return principal != null && principal.can(permission);
  }
}
