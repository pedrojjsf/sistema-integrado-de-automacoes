package com.accord.sia.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoleMappingTest {

  @Test
  void mapeiaAppRolesPadraoDoIdp() {
    var roles = RoleMapping.appRoles().map(List.of("sia.viewer", "sia.operator"));

    assertThat(roles).containsExactlyInAnyOrder(SiaRole.VIEWER, SiaRole.OPERATOR);
  }

  @Test
  void ignoraValorDesconhecidoParaNaoEscalarPrivilegio() {
    var roles = RoleMapping.appRoles().map(List.of("Domain Admins", "sia.viewer"));

    assertThat(roles).containsExactly(SiaRole.VIEWER);
  }

  @Test
  void aceitaClaimEmCaixaDiferente() {
    assertThat(RoleMapping.appRoles().map(List.of("SIA.Admin"))).containsExactly(SiaRole.ADMIN);
  }

  @Test
  void semClaimNaoConcedePapel() {
    assertThat(RoleMapping.appRoles().map(null)).isEmpty();
    assertThat(RoleMapping.appRoles().map(List.of())).isEmpty();
  }

  @Test
  void mapaCustomizadoCobreGuidDeGrupo() {
    var guid = "3f9a1c2e-0000-0000-0000-000000000001";
    var mapping = RoleMapping.of(Map.of(guid, SiaRole.OPERATOR));

    assertThat(mapping.map(List.of(guid))).containsExactly(SiaRole.OPERATOR);
  }
}
