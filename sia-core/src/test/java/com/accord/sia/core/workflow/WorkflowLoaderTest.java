package com.accord.sia.core.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WorkflowLoaderTest {

  private final WorkflowLoader loader = new WorkflowLoader();

  private static final String VALID =
      """
      name: organizar-documentos
      description: baixa, classifica e move
      steps:
        - id: baixar
          use: receitanet.baixar
          with:
            pedido: "${inputs.pedido}"
        - id: classificar
          use: ia.classificar
          needs: [baixar]
          with:
            arquivo: "${steps.baixar.outputs.arquivo}"
          onError: CONTINUE
        - id: mover
          use: arquivos.mover
          needs: [classificar]
      """;

  @Test
  void leWorkflowValido() throws Exception {
    WorkflowDefinition definition = loader.parse(VALID);

    assertThat(definition.name()).isEqualTo("organizar-documentos");
    assertThat(definition.steps()).hasSize(3);
    assertThat(definition.step("classificar").needs()).containsExactly("baixar");
    assertThat(definition.step("classificar").onError()).isEqualTo(WorkflowStep.OnError.CONTINUE);
  }

  @Test
  void ordemTopologicaRespeitaDependencias() throws Exception {
    WorkflowDefinition definition = loader.parse(VALID);

    assertThat(loader.topologicalOrder(definition))
        .extracting(WorkflowStep::id)
        .containsExactly("baixar", "classificar", "mover");
  }

  @Test
  void rejeitaPassoDuplicado() {
    assertThatThrownBy(() ->
            loader.parse(
                """
                name: x
                steps:
                  - { id: a, use: p.a }
                  - { id: a, use: p.b }
                """))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicado");
  }

  @Test
  void rejeitaDependenciaInexistente() {
    assertThatThrownBy(() ->
            loader.parse(
                """
                name: x
                steps:
                  - { id: a, use: p.a, needs: [nao-existe] }
                """))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("nao-existe");
  }

  @Test
  void rejeitaCiclo() {
    assertThatThrownBy(() ->
            loader.parse(
                """
                name: x
                steps:
                  - { id: a, use: p.a, needs: [c] }
                  - { id: b, use: p.b, needs: [a] }
                  - { id: c, use: p.c, needs: [b] }
                """))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ciclo");
  }

  @Test
  void rejeitaAutoDependencia() {
    assertThatThrownBy(() ->
            loader.parse(
                """
                name: x
                steps:
                  - { id: a, use: p.a, needs: [a] }
                """))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("si mesmo");
  }

  @Test
  void rejeitaUseForaDoFormato() {
    assertThatThrownBy(() ->
            loader.parse(
                """
                name: x
                steps:
                  - { id: a, use: semPonto }
                """))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("plugin.acao");
  }

  @Test
  void rejeitaTemplateParaPassoInexistente() {
    assertThatThrownBy(() ->
            loader.parse(
                """
                name: x
                steps:
                  - id: a
                    use: p.a
                    with:
                      valor: "${steps.fantasma.outputs.x}"
                """))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fantasma");
  }

  @Test
  void rejeitaCampoDesconhecido() {
    assertThatThrownBy(() ->
            loader.parse(
                """
                name: x
                passos: []
                """))
        .isInstanceOf(java.io.IOException.class);
  }
}
