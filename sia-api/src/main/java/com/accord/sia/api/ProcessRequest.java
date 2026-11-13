package com.accord.sia.api;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Pedido de execucao de processo externo.
 *
 * <p>{@code executable} e um <b>nome logico</b> configurado no Core (allowlist), nunca um caminho
 * de disco: se o plugin pudesse escolher o binario, a permissao {@code process} viraria execucao
 * arbitraria.
 *
 * @param executable nome logico registrado na allowlist (ex.: {@code receitanet-bx})
 * @param arguments argumentos adicionados ao comando base, na ordem
 * @param timeout limite de execucao; nulo usa o padrao do Core
 * @param redactValues valores que devem ser mascarados na saida capturada
 * @param workingDirectory diretorio de trabalho; nulo herda o do daemon
 */
public record ProcessRequest(
    String executable,
    List<String> arguments,
    Duration timeout,
    List<String> redactValues,
    Path workingDirectory) {

  public ProcessRequest {
    Objects.requireNonNull(executable, "executable");
    arguments = arguments == null ? List.of() : List.copyOf(arguments);
    redactValues = redactValues == null ? List.of() : List.copyOf(redactValues);
  }

  public ProcessRequest(
      String executable, List<String> arguments, Duration timeout, List<String> redactValues) {
    this(executable, arguments, timeout, redactValues, null);
  }

  public static ProcessRequest of(String executable, String... arguments) {
    return new ProcessRequest(executable, List.of(arguments), null, List.of(), null);
  }

  public static ProcessRequest of(String executable, List<String> arguments) {
    return new ProcessRequest(executable, arguments, null, List.of(), null);
  }

  public ProcessRequest withTimeout(Duration newTimeout) {
    return new ProcessRequest(executable, arguments, newTimeout, redactValues, workingDirectory);
  }

  public ProcessRequest withRedactedValues(String... values) {
    return new ProcessRequest(executable, arguments, timeout, List.of(values), workingDirectory);
  }

  public ProcessRequest withWorkingDirectory(Path directory) {
    return new ProcessRequest(executable, arguments, timeout, redactValues, directory);
  }
}
