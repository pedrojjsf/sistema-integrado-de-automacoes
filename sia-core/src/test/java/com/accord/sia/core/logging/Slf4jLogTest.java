package com.accord.sia.core.logging;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class Slf4jLogTest {

  private final Slf4jLog log = new Slf4jLog("alpha-plugin");

  @Test
  void aceitaTodosOsNiveisSemLancar() {
    assertThatCode(
            () -> {
              log.debug("detalhe");
              log.info("informacao");
              log.warn("atencao");
              log.error("falhou");
              log.error("falhou com causa", new IllegalStateException("causa"));
              log.event("RUN_STARTED", "execucao iniciada");
              log.info(null);
            })
        .doesNotThrowAnyException();
  }
}
