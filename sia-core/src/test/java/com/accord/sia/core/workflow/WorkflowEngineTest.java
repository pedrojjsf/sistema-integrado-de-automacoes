package com.accord.sia.core.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.accord.sia.api.Parameters;
import com.accord.sia.core.persistence.Database;
import com.accord.sia.core.persistence.SchemaMigrator;
import com.accord.sia.core.persistence.SingleWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkflowEngineTest {

  @TempDir Path temp;

  private SingleWriter writer;
  private WorkflowRepository repository;
  private WorkflowEngine engine;
  private final List<String> executed = new CopyOnWriteArrayList<>();
  private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
  private final Map<String, Map<String, Object>> outputsByAutomation = new ConcurrentHashMap<>();
  private final List<Parameters> receivedParams = new CopyOnWriteArrayList<>();

  @BeforeEach
  void setUp() throws IOException {
    Database database = Database.open(temp.resolve("sia.db"));
    new SchemaMigrator().migrate(database);
    writer = new SingleWriter();
    repository = new WorkflowRepository(database, writer, Clock.systemUTC());
    engine = new WorkflowEngine(repository, this::step);
  }

  @AfterEach
  void tearDown() {
    engine.close();
    writer.close();
  }

  private StepExecutor.StepOutcome step(String automationId, Parameters params) {
    attempts.computeIfAbsent(automationId, key -> new AtomicInteger()).incrementAndGet();
    executed.add(automationId);
    receivedParams.add(params);
    if (automationId.startsWith("falha")) {
      return StepExecutor.StepOutcome.failure("FALHOU", "de proposito");
    }
    if (automationId.equals("retry.a") && attempts.get(automationId).get() <= 2) {
      return StepExecutor.StepOutcome.failure("FALHOU", "de proposito");
    }
    return StepExecutor.StepOutcome.success(
        outputsByAutomation.getOrDefault(automationId, Map.of("ok", "true")));
  }

  @Test
  void executaEmOrdemEDepoisEmParalelo() {
    WorkflowDefinition definition =
        load(
            """
            name: linear
            steps:
              - { id: a, use: p.a }
              - { id: b, use: p.b, needs: [a] }
            """);

    var run = engine.run(definition, yaml(), Map.of());

    assertThat(run.status()).isEqualTo("SUCCESS");
    assertThat(executed).containsExactly("p.a", "p.b");
    assertThat(run.nodeStatuses()).containsEntry("a", "DONE").containsEntry("b", "DONE");
  }

  @Test
  void resolveTemplateComSaidaDoPassoAnterior() {
    outputsByAutomation.put("p.baixar", Map.of("arquivo", "sped-2024.txt"));
    WorkflowDefinition definition =
        load(
            """
            name: encadeado
            steps:
              - { id: baixar, use: p.baixar }
              - id: classificar
                use: p.classificar
                needs: [baixar]
                with:
                  arquivo: "${steps.baixar.outputs.arquivo}"
                  origem: "${inputs.origem}"
            """);

    var run = engine.run(definition, yaml(), Map.of("origem", "receita"));

    assertThat(run.status()).isEqualTo("SUCCESS");
    Parameters params = receivedParams.get(receivedParams.size() - 1);
    assertThat(params.string("arquivo")).contains("sped-2024.txt");
    assertThat(params.string("origem")).contains("receita");
  }

  @Test
  void falhaComFailInterrompeEDemaisFicamSkipped() {
    WorkflowDefinition definition =
        load(
            """
            name: falha
            steps:
              - { id: a, use: falha.a }
              - { id: b, use: p.b, needs: [a] }
              - { id: c, use: p.c, needs: [b] }
            """);

    var run = engine.run(definition, yaml(), Map.of());

    assertThat(run.status()).isEqualTo("FAILURE");
    assertThat(executed).containsExactly("falha.a");
    assertThat(run.nodeStatuses())
        .containsEntry("a", "FAILED")
        .containsEntry("b", "SKIPPED")
        .containsEntry("c", "SKIPPED");
  }

  @Test
  void continueSegueComOsPassosIndependentes() {
    WorkflowDefinition definition =
        load(
            """
            name: tolerante
            steps:
              - { id: a, use: falha.a, onError: CONTINUE }
              - { id: b, use: p.b }
            """);

    var run = engine.run(definition, yaml(), Map.of());

    assertThat(run.status()).isEqualTo("PARTIAL");
    assertThat(executed).containsExactlyInAnyOrder("falha.a", "p.b");
    assertThat(run.nodeStatuses()).containsEntry("a", "FAILED").containsEntry("b", "DONE");
  }

  @Test
  void retryTentaNovamenteAntesDeDesistir() {
    WorkflowDefinition definition =
        load(
            """
            name: retry
            steps:
              - { id: a, use: retry.a, onError: RETRY, retries: 2 }
            """);

    var run = engine.run(definition, yaml(), Map.of());

    assertThat(run.status()).isEqualTo("SUCCESS");
    assertThat(attempts.get("retry.a").get()).isEqualTo(3);
  }

  @Test
  void retomadaPulaPassosConcluidos() throws Exception {
    String workflowYaml =
        """
        name: retomavel
        steps:
          - { id: a, use: p.a }
          - { id: b, use: falha.b, needs: [a] }
        """;
    var first = engine.run(load(workflowYaml), workflowYaml, Map.of());

    assertThat(first.status()).isEqualTo("FAILURE");
    assertThat(executed).containsExactly("p.a", "falha.b");

    executed.clear();
    var resumed = engine.resume(first.id());

    assertThat(executed).containsExactly("falha.b");
    assertThat(resumed.status()).isEqualTo("FAILURE");
  }

  private static WorkflowDefinition load(String yaml) {
    try {
      return new WorkflowLoader().parse(yaml);
    } catch (IOException error) {
      throw new IllegalStateException(error);
    }
  }

  private static String yaml() {
    return "name: x";
  }
}
