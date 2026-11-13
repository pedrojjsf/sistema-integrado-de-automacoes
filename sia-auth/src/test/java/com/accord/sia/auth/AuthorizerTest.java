package com.accord.sia.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthorizerTest {

  private final Authorizer authorizer = new Authorizer();

  private static Principal principalWith(SiaRole... roles) {
    return Principal.user("sub-1", "analista@accord", Set.of(roles));
  }

  @Test
  void viewerLeMasNaoExecuta() {
    var viewer = principalWith(SiaRole.VIEWER);

    assertThat(viewer.can(Permission.RUN_READ)).isTrue();
    assertThat(viewer.can(Permission.PLUGIN_READ)).isTrue();
    assertThat(viewer.can(Permission.RUN_EXECUTE)).isFalse();
    assertThat(viewer.can(Permission.PLUGIN_MANAGE)).isFalse();
  }

  @Test
  void operatorExecutaMasNaoGerenciaPlugin() {
    var operator = principalWith(SiaRole.OPERATOR);

    assertThat(operator.can(Permission.RUN_EXECUTE)).isTrue();
    assertThat(operator.can(Permission.RUN_CANCEL)).isTrue();
    assertThat(operator.can(Permission.PLUGIN_MANAGE)).isFalse();
  }

  @Test
  void adminConcedeTodasAsPermissoes() {
    var admin = principalWith(SiaRole.ADMIN);

    for (Permission permission : Permission.values()) {
      assertThat(admin.can(permission)).as("permissao %s", permission).isTrue();
    }
  }

  @Test
  void semIdentidadeResultaEmNaoAutenticado() {
    assertThatThrownBy(() -> authorizer.require(null, Permission.RUN_READ))
        .isInstanceOfSatisfying(
            AuthorizationException.class,
            error ->
                assertThat(error.reason())
                    .isEqualTo(AuthorizationException.Reason.UNAUTHENTICATED));
  }

  @Test
  void autenticadoSemPermissaoResultaEmProibido() {
    assertThatThrownBy(
            () -> authorizer.require(principalWith(SiaRole.VIEWER), Permission.RUN_EXECUTE))
        .isInstanceOfSatisfying(
            AuthorizationException.class,
            error -> assertThat(error.reason()).isEqualTo(AuthorizationException.Reason.FORBIDDEN));
  }

  @Test
  void identidadeDeSistemaTemPoderTotal() {
    var system = Principal.system("scheduler");

    assertThat(system.origin()).isEqualTo(Principal.Origin.SYSTEM);
    assertThat(system.can(Permission.PLUGIN_MANAGE)).isTrue();
    assertThat(system.subject()).isEqualTo("system:scheduler");
  }

  @Test
  void allowsNaoLancaExcecao() {
    assertThat(authorizer.allows(null, Permission.RUN_READ)).isFalse();
    assertThat(authorizer.allows(principalWith(SiaRole.VIEWER), Permission.RUN_READ)).isTrue();
  }
}
