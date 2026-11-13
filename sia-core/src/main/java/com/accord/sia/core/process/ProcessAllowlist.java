package com.accord.sia.core.process;

import com.accord.sia.api.ProcessException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nomes logicos que um plugin pode executar, mapeados para o comando base.
 *
 * <p>Fica em configuracao, nao em codigo: o administrador define o que existe, o plugin apenas
 * escolhe entre os nomes permitidos.
 */
public final class ProcessAllowlist {

  public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);

  /**
   * @param baseCommand comando ja resolvido (ex.: {@code ["java","-cp","...","Main"]})
   * @param defaultTimeout usado quando o pedido nao define timeout
   */
  public record Spec(List<String> baseCommand, Duration defaultTimeout) {

    public Spec {
      if (baseCommand == null || baseCommand.isEmpty()) {
        throw new IllegalArgumentException("comando base vazio");
      }
      baseCommand = List.copyOf(baseCommand);
      defaultTimeout = defaultTimeout == null ? DEFAULT_TIMEOUT : defaultTimeout;
    }
  }

  private final Map<String, Spec> specs;

  private ProcessAllowlist(Map<String, Spec> specs) {
    this.specs = Map.copyOf(specs);
  }

  public static ProcessAllowlist of(Map<String, List<String>> commands) {
    Map<String, Spec> specs = new LinkedHashMap<>();
    commands.forEach((name, command) -> specs.put(name, new Spec(command, DEFAULT_TIMEOUT)));
    return new ProcessAllowlist(specs);
  }

  public static ProcessAllowlist ofSpecs(Map<String, Spec> specs) {
    return new ProcessAllowlist(specs);
  }

  public Spec spec(String name) {
    Spec spec = specs.get(name);
    if (spec == null) {
      throw new ProcessException(
          ProcessException.Reason.NOT_ALLOWED, "executavel nao permitido: " + name);
    }
    return spec;
  }

  public boolean allows(String name) {
    return specs.containsKey(name);
  }
}
