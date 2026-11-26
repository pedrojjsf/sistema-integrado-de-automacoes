package com.accord.sia.core.process;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessPluginHostTest {

  @Test
  void awaitQuandoProcessoMorreSemResponderViraIllegalState() throws Exception {
    // regressao: o EOF chega antes de o SO colher o processo e exitValue() lança
    // IllegalThreadStateException, que vazava como erro nao relacionado
    String java =
        Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java")
            .toString();

    try (ProcessPluginHost host = ProcessPluginHost.start(List.of(java, "-version"), null, null)) {
      assertThatThrownBy(() -> host.await(Duration.ofSeconds(10)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("terminou a execucao");
    }
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
  }
}
