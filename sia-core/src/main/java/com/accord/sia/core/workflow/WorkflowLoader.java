package com.accord.sia.core.workflow;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Le e valida um {@code workflow.yml}.
 *
 * <p>Validacao acontece antes de qualquer execucao: ciclo, dependencia inexistente e referencia de
 * template quebrada sao erro de definicao, nao falha de runtime.
 */
public final class WorkflowLoader {

  private static final Pattern TEMPLATE =
      Pattern.compile(
          "\\$\\{(?:inputs|steps)\\.([A-Za-z0-9_-]+)(?:\\.outputs\\.([A-Za-z0-9_-]+))?}");

  private final ObjectMapper mapper =
      new ObjectMapper(new YAMLFactory())
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

  public WorkflowDefinition read(Path file) throws IOException {
    return parse(Files.readString(file, StandardCharsets.UTF_8));
  }

  public WorkflowDefinition parse(String yaml) throws IOException {
    WorkflowDefinition definition = mapper.readValue(yaml, WorkflowDefinition.class);
    validate(definition);
    return definition;
  }

  public void validate(WorkflowDefinition definition) throws IOException {
    if (definition.name() == null || definition.name().isBlank()) {
      throw new IllegalArgumentException("workflow.name obrigatorio");
    }
    if (definition.steps().isEmpty()) {
      throw new IllegalArgumentException("workflow sem passos");
    }
    Set<String> ids = new HashSet<>();
    for (WorkflowStep step : definition.steps()) {
      if (step.id() == null || step.id().isBlank()) {
        throw new IllegalArgumentException("passo sem id");
      }
      if (!ids.add(step.id())) {
        throw new IllegalArgumentException("passo duplicado: " + step.id());
      }
      if (step.use() == null || !step.use().matches("^[a-z][a-z0-9-]*\\.[a-z][a-z0-9-]*$")) {
        throw new IllegalArgumentException(
            "passo " + step.id() + ": use precisa ser 'plugin.acao'");
      }
    }
    for (WorkflowStep step : definition.steps()) {
      for (String need : step.needs()) {
        if (!ids.contains(need)) {
          throw new IllegalArgumentException(
              "passo " + step.id() + " depende de passo inexistente: " + need);
        }
        if (need.equals(step.id())) {
          throw new IllegalArgumentException("passo " + step.id() + " depende de si mesmo");
        }
      }
      validateTemplates(step, ids);
    }
    topologicalOrder(definition);
  }

  private void validateTemplates(WorkflowStep step, Set<String> ids) {
    for (Object value : step.with().values()) {
      if (!(value instanceof String text)) {
        continue;
      }
      Matcher matcher = TEMPLATE.matcher(text);
      while (matcher.find()) {
        String reference = matcher.group(1);
        if (matcher.group(2) == null) {
          continue; // ${inputs.x}
        }
        if (!ids.contains(reference)) {
          throw new IllegalArgumentException(
              "passo " + step.id() + " referencia passo inexistente no template: " + reference);
        }
        if (reference.equals(step.id())) {
          throw new IllegalArgumentException("passo " + step.id() + " referencia a propria saida");
        }
      }
    }
  }

  /** Ordem topologica; ciclo vira excecao. */
  public List<WorkflowStep> topologicalOrder(WorkflowDefinition definition) {
    List<WorkflowStep> ordered = new ArrayList<>();
    Set<String> done = new HashSet<>();
    List<WorkflowStep> pending = new ArrayList<>(definition.steps());
    while (!pending.isEmpty()) {
      boolean progressed = false;
      for (WorkflowStep step : new ArrayList<>(pending)) {
        if (done.containsAll(step.needs())) {
          ordered.add(step);
          done.add(step.id());
          pending.remove(step);
          progressed = true;
        }
      }
      if (!progressed) {
        throw new IllegalArgumentException(
            "ciclo no workflow entre: " + pending.stream().map(WorkflowStep::id).toList());
      }
    }
    return List.copyOf(ordered);
  }
}
