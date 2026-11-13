package com.accord.sia.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParametersTest {

  @Test
  void leValoresTipados() {
    var params =
        Parameters.of(
            Map.of(
                "to", "a@b.c", "tentativas", 3, "seguro", true, "limite", new BigDecimal("10.50")));

    assertThat(params.string("to")).contains("a@b.c");
    assertThat(params.integer("tentativas", 0)).isEqualTo(3);
    assertThat(params.bool("seguro", false)).isTrue();
    assertThat(params.decimal("limite")).isEqualByComparingTo("10.50");
  }

  @Test
  void converteTextoParaTiposPrimitivos() {
    var params = Parameters.of(Map.of("tentativas", "42", "seguro", "true"));

    assertThat(params.integer("tentativas", 0)).isEqualTo(42);
    assertThat(params.bool("seguro", false)).isTrue();
  }

  @Test
  void falhaAoPedirTipoErradoAoInvesDeConverterEmSilencio() {
    var params = Parameters.of("tentativas", 3);

    assertThatThrownBy(() -> params.string("tentativas"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tentativas");
  }

  @Test
  void ausenteRetornaVazioOuPadrao() {
    var params = Parameters.empty();

    assertThat(params.string("to")).isEmpty();
    assertThat(params.string("to", "padrao")).isEqualTo("padrao");
    assertThat(params.integer("x", 7)).isEqualTo(7);
    assertThat(params.has("to")).isFalse();
  }

  @Test
  void requireStringFalhaQuandoAusente() {
    assertThatThrownBy(() -> Parameters.empty().requireString("to"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("to");
  }

  @Test
  void withoutRemoveSegredosAntesDeLogar() {
    var params = Parameters.of(Map.of("to", "a@b.c", "smtpPassword", "segredo"));

    var safe = params.without("smtpPassword");

    assertThat(safe.names()).containsExactly("to");
    assertThat(safe.has("smtpPassword")).isFalse();
    assertThat(params.has("smtpPassword")).isTrue();
  }

  @Test
  void mergeSobrescreveValoresDoOutro() {
    var base = Parameters.of("a", "1");
    var override = Parameters.of(Map.of("a", "2", "b", "3"));

    var merged = base.merge(override);

    assertThat(merged.string("a")).contains("2");
    assertThat(merged.string("b")).contains("3");
  }

  @Test
  void mapaExpostoEhImutavel() {
    var params = Parameters.of("a", "1");

    assertThatThrownBy(() -> params.asMap().put("b", "2"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void listaSomenteAsChavesNoToString() {
    var params = Parameters.of(Map.of("to", "a@b.c", "smtpPassword", "segredo"));

    assertThat(params.toString()).contains("to").contains("smtpPassword").doesNotContain("segredo");
  }

  @Test
  void nomesSaoExpostosParaAuditoriaSemValores() {
    var params = Parameters.of(Map.of("to", "a@b.c", "assunto", "teste"));

    assertThat(List.copyOf(params.names())).containsExactlyInAnyOrder("to", "assunto");
  }
}
