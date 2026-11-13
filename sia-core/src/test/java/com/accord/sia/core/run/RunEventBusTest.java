package com.accord.sia.core.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunEventBusTest {

  private final RunEventBus bus = new RunEventBus();

  @Test
  void entregaEventosApenasParaARunInscrita() throws Exception {
    List<RunEvent> first = new ArrayList<>();
    List<RunEvent> second = new ArrayList<>();

    try (AutoCloseable ignored = bus.subscribe("run-1", first::add);
        AutoCloseable ignoredToo = bus.subscribe("run-2", second::add)) {
      bus.publish(RunEvent.log("run-1", "INFO", "passo 1", Instant.now()));
      bus.publish(RunEvent.progress("run-1", "50%", Instant.now()));
      bus.publish(RunEvent.step("run-2", "outro", Instant.now()));
    }

    assertThat(first).hasSize(2);
    assertThat(first.get(0).message()).isEqualTo("passo 1");
    assertThat(second).hasSize(1);
  }

  @Test
  void paraDeEntregarAposCancelarInscricao() throws Exception {
    List<RunEvent> received = new ArrayList<>();
    AutoCloseable subscription = bus.subscribe("run-3", received::add);

    bus.publish(RunEvent.log("run-3", "INFO", "antes", Instant.now()));
    subscription.close();
    bus.publish(RunEvent.log("run-3", "INFO", "depois", Instant.now()));

    assertThat(received).hasSize(1);
  }

  @Test
  void listenerQuebradoNaoInterrompeOsDemais() {
    List<RunEvent> received = new ArrayList<>();
    bus.subscribe(
        "run-4",
        event -> {
          throw new IllegalStateException("cliente caiu");
        });
    bus.subscribe("run-4", received::add);

    bus.publish(RunEvent.finished("run-4", "SUCCESS", Instant.now()));

    assertThat(received).hasSize(1);
  }
}
