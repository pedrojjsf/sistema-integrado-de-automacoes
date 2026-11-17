package com.accord.integrations.receitanet;

import static org.assertj.core.api.Assertions.assertThat;

import com.accord.sia.api.ProcessResult;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class BotOutputTest {

  private static ProcessResult result(int exitCode, String stdout, String stderr) {
    return new ProcessResult(exitCode, stdout, stderr, Duration.ofSeconds(1), false);
  }

  @Test
  void traduzBancoOcupado() {
    ProcessResult failure =
        result(1, "", "ERROR XSDB6: Another instance of Derby may have already booted");

    assertThat(BotOutput.classify(failure)).isEqualTo("BOT_BANCO_OCUPADO");
  }

  @Test
  void traduzBancoDeOutraVersao() {
    ProcessResult failure =
        result(1, "", "ERROR 42818: Comparisons between 'BIGINT' and 'CHAR' are not supported");

    assertThat(BotOutput.classify(failure)).isEqualTo("BOT_BANCO_INCOMPATIVEL");
    assertThat(BotOutput.classify(result(1, "", "ERROR 42S02: table not found")))
        .isEqualTo("BOT_BANCO_INCOMPATIVEL");
  }

  @Test
  void traduzBancoInacessivel() {
    assertThat(BotOutput.classify(result(1, "", "XJ040: Failed to start database")))
        .isEqualTo("BOT_BANCO_INACESSIVEL");
  }

  @Test
  void falhaDesconhecidaViraBotFalhou() {
    assertThat(BotOutput.classify(result(2, "algo deu errado", ""))).isEqualTo("BOT_FALHOU");
  }

  @Test
  void detalhePrefereStderr() {
    ProcessResult failure = result(1, "saida padrao", "erro real");

    assertThat(BotOutput.detail(failure)).isEqualTo("erro real");
  }

  @Test
  void detalheUsaStdoutQuandoNaoHaStderr() {
    assertThat(BotOutput.detail(result(1, "erro no stdout", ""))).isEqualTo("erro no stdout");
  }

  @Test
  void detalheVazioTemMensagemPropria() {
    assertThat(BotOutput.detail(result(7, "", ""))).contains("codigo 7");
  }

  @Test
  void ultimaLinhaIgnoraLinhasVazias() {
    assertThat(BotOutput.lastLine("linha 1\n\nlinha 2\n\n")).isEqualTo("linha 2");
    assertThat(BotOutput.lastLine("")).isEmpty();
  }
}
