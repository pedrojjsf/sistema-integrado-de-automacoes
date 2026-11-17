package com.accord.sia.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AutomationResultTest {

  @Test
  void sucessoGuardaSomenteAsChavesDeSaida() {
    var result = AutomationResult.success(Map.of("messageId", "abc"), "enviado");

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.outputs()).containsEntry("messageId", "abc");
    assertThat(result.status()).isEqualTo(AutomationResult.Status.SUCCESS);
    assertThat(result.errorCode()).isNull();
  }

  @Test
  void falhaExigeErrorCode() {
    assertThatThrownBy(() -> AutomationResult.failure(null, "erro"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("errorCode");

    assertThatThrownBy(() -> AutomationResult.failure("  ", "erro"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void sucessoNaoAceitaErrorCode() {
    assertThatThrownBy(
            () -> new AutomationResult(AutomationResult.Status.SUCCESS, Map.of(), "E1", "ok"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("FAILURE");
  }

  @Test
  void skippedNaoTemErro() {
    var result = AutomationResult.skipped("sem anexos");

    assertThat(result.status()).isEqualTo(AutomationResult.Status.SKIPPED);
    assertThat(result.outputs()).isEmpty();
  }

  @Test
  void outputsSaoCopiadosNaConstrucao() {
    Map<String, Object> mutable = new HashMap<>();
    mutable.put("k", "v");

    var result = AutomationResult.success(mutable, null);
    mutable.put("k", "alterado");

    assertThat(result.outputs()).containsEntry("k", "v");
  }
}
