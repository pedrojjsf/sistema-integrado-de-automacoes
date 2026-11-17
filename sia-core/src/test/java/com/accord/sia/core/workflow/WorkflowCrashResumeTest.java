package com.accord.sia.core.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.accord.sia.core.persistence.Database;
import com.accord.sia.core.persistence.SchemaMigrator;
import com.accord.sia.core.persistence.SingleWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Teste de caos do workflow: o daemon morre no meio do passo 2 e outro processo retoma do banco.
 *
 * <p>Simula a queda fechando o engine (threads interrompidas) enquanto o passo esta bloqueado — o
 * que a persistencia enxerga e o mesmo que um {@code kill -9}: passo 1 gravado, passo 2
 * interrompido.
 */
class WorkflowCrashResumeTest {

  @TempDir Path temp;

  private static final String DEFINITION =
      """
      name: caos
      steps:
        - { id: a, use: p.a }
        - { id: b, use: p.b, needs: [a] }
      """;

  @Test
  void retomaWorkflowInterrompidoNoMeio() throws Exception {
    Database database = Database.open(temp.resolve("sia.db"));
    new SchemaMigrator().migrate(database);
    try (SingleWriter writer = new SingleWriter()) {
      WorkflowRepository repository = new WorkflowRepository(database, writer, Clock.systemUTC());
      CountDownLatch startedB = new CountDownLatch(1);
      CountDownLatch releaseB = new CountDownLatch(1);

      WorkflowEngine crashed =
          new WorkflowEngine(
              repository,
              (automationId, params) -> {
                if (automationId.equals("p.b")) {
                  startedB.countDown();
                  try {
                    releaseB.await(30, TimeUnit.SECONDS);
                  } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return StepExecutor.StepOutcome.failure("INTERRUPTED", "engine encerrado");
                  }
                }
                return StepExecutor.StepOutcome.success(Map.of("ok", "true"));
              });

      Thread runner =
          new Thread(() -> crashed.run(load(DEFINITION), DEFINITION, Map.of()), "workflow-crash");
      runner.start();
      assertThat(startedB.await(10, TimeUnit.SECONDS)).isTrue();

      // daemon "morre": engine fechado no meio do passo b
      crashed.close();
      runner.join(10_000);

      String runId = repository.recent(1).get(0).id();
      assertThat(repository.nodes(runId)).containsKey("a");

      List<String> executedAfterRestart = new CopyOnWriteArrayList<>();
      WorkflowEngine restarted =
          new WorkflowEngine(
              repository,
              (automationId, params) -> {
                executedAfterRestart.add(automationId);
                return StepExecutor.StepOutcome.success(Map.of("ok", "true"));
              });

      var resumed = restarted.resume(runId);

      assertThat(executedAfterRestart).containsExactly("p.b");
      assertThat(resumed.status()).isEqualTo("SUCCESS");
      assertThat(resumed.nodeStatuses()).containsEntry("a", "DONE").containsEntry("b", "DONE");
      restarted.close();
    }
  }

  private static WorkflowDefinition load(String yaml) {
    try {
      return new WorkflowLoader().parse(yaml);
    } catch (IOException error) {
      throw new IllegalStateException(error);
    }
  }
}
