package com.accord.integrations.receitanet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.accord.sia.api.AutomationException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BotArgumentsTest {

  @TempDir Path temp;

  @Test
  void aceitaDataValida() throws Exception {
    assertThat(BotArguments.date("01/03/2024", "inicio")).isEqualTo(LocalDate.of(2024, 3, 1));
  }

  @Test
  void recusaDataInexistente() {
    assertThatThrownBy(() -> BotArguments.date("31/04/2024", "inicio"))
        .isInstanceOf(AutomationException.class)
        .hasMessageContaining("inicio");
    assertThatThrownBy(() -> BotArguments.date("30/02/2024", "fim"))
        .isInstanceOf(AutomationException.class);
    assertThatThrownBy(() -> BotArguments.date("29/02/2023", "fim"))
        .isInstanceOf(AutomationException.class);
  }

  @Test
  void aceita29DeFevereiroEmAnoBissexto() throws Exception {
    assertThat(BotArguments.date("29/02/2024", "fim")).isEqualTo(LocalDate.of(2024, 2, 29));
  }

  @Test
  void recusaFormatoDiferente() {
    assertThatThrownBy(() -> BotArguments.date("2024-03-01", "inicio"))
        .isInstanceOf(AutomationException.class);
  }

  @Test
  void normalizaListaDeSistemas() throws Exception {
    assertThat(BotArguments.systemIds("1, 13 ,11")).containsExactly("1", "13", "11");
  }

  @Test
  void recusaSistemaNaoNumerico() {
    assertThatThrownBy(() -> BotArguments.systemIds("1,SPED"))
        .isInstanceOf(AutomationException.class)
        .hasMessageContaining("SPED");
  }

  @Test
  void recusaListaVazia() {
    assertThatThrownBy(() -> BotArguments.systemIds(" , ")).isInstanceOf(AutomationException.class);
  }

  @Test
  void exigeArquivoExistente() throws Exception {
    Path file = Files.writeString(temp.resolve("cnpjs.txt"), "23965501000108");

    assertThat(BotArguments.readableFile(file.toString())).isEqualTo(file);
    assertThatThrownBy(() -> BotArguments.readableFile(temp.resolve("nao-existe.txt").toString()))
        .isInstanceOf(AutomationException.class)
        .hasMessageContaining("nao encontrado");
  }
}
