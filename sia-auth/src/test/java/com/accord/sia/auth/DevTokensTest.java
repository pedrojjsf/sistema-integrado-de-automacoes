package com.accord.sia.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DevTokensTest {

  private static final Principal ANALYST =
      Principal.user("sub-analista", "analista@accord", Set.of(SiaRole.OPERATOR));

  @Test
  void resolveTokenConfigurado() {
    var devTokens = DevTokens.forEnvironment("local", Map.of("token-local", ANALYST));

    assertThat(devTokens.authenticate("token-local")).contains(ANALYST);
  }

  @Test
  void tokenDesconhecidoNaoAutentica() {
    var devTokens = DevTokens.forEnvironment("local", Map.of("token-local", ANALYST));

    assertThat(devTokens.authenticate("outro-token")).isEmpty();
  }

  @Test
  void tokenEmBrancoNaoAutentica() {
    var devTokens = DevTokens.forEnvironment("local", Map.of("token-local", ANALYST));

    assertThat(devTokens.authenticate(null)).isEmpty();
    assertThat(devTokens.authenticate("  ")).isEmpty();
  }

  @Test
  void recusaSubirEmProducao() {
    assertThatThrownBy(() -> DevTokens.forEnvironment("prod", Map.of("t", ANALYST)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("producao");
  }

  @Test
  void exigeAoMenosUmToken() {
    assertThatThrownBy(() -> DevTokens.forEnvironment("local", Map.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
