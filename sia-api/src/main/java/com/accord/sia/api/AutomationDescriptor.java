package com.accord.sia.api;

import java.util.List;
import java.util.Objects;

/**
 * Tudo que o Core precisa saber sobre uma automacao, sem executar codigo do plugin.
 *
 * @param id identificador qualificado, no formato {@code plugin.acao} (ex.: {@code email.send})
 * @param name nome legivel
 * @param description o que a automacao faz
 * @param params parametros aceitos; cada um declara tipo e sensibilidade
 * @param persistOutputs se o resultado pode ser gravado; padrao {@code false}, porque a saida pode
 *     conter dado pessoal
 */
public record AutomationDescriptor(
    String id, String name, String description, List<ParamSpec> params, boolean persistOutputs) {

  public AutomationDescriptor {
    Objects.requireNonNull(id, "id");
    params = params == null ? List.of() : List.copyOf(params);
  }

  public static AutomationDescriptor of(String id, String name, String description) {
    return new AutomationDescriptor(id, name, description, List.of(), false);
  }

  public static AutomationDescriptor of(
      String id, String name, String description, List<ParamSpec> params) {
    return new AutomationDescriptor(id, name, description, params, false);
  }

  public AutomationDescriptor withPersistedOutputs() {
    return new AutomationDescriptor(id, name, description, params, true);
  }

  public ParamSpec param(String name) {
    return params.stream()
        .filter(spec -> spec.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("parametro nao declarado: " + name));
  }
}
