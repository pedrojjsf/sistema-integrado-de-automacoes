package com.accord.sia.core.process;

import java.util.Map;

/**
 * Ambiente de execução de processos filhos.
 *
 * <p>O daemon resolve segredos de plugins a partir de variáveis de ambiente ({@code
 * SIA_SECRET_&lt;plugin&gt;_&lt;nome&gt;}, ver ADR-0004/D9). Um processo iniciado por plugin herda
 * o ambiente do daemon, então herdaria esses segredos de graça. Antes de subir qualquer processo, o
 * SIA remove as variáveis de segredo.
 */
public final class SafeEnvironment {

  private static final String SECRET_PREFIX = "SIA_SECRET_";

  private SafeEnvironment() {}

  /** Remove as variáveis de segredo do ambiente que o {@link ProcessBuilder} herdaria. */
  public static void apply(ProcessBuilder builder) {
    removeSecrets(builder.environment());
  }

  /** Purga qualquer variável cujo nome comece com {@code SIA_SECRET_}. */
  public static void removeSecrets(Map<String, String> environment) {
    environment.keySet().removeIf(name -> name != null && name.startsWith(SECRET_PREFIX));
  }
}
