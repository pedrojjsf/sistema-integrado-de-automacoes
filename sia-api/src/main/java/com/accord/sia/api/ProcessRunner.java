package com.accord.sia.api;

/**
 * Execucao de processos externos, controlada pelo Core.
 *
 * <p>Existe para que um plugin consiga orquestrar ferramentas de linha de comando sem receber
 * {@code ProcessBuilder}: o Core resolve o binario pela allowlist, aplica timeout, limita a saida e
 * redige valores sensiveis. Exige a permissao {@link PluginPermission#PROCESS}.
 */
public interface ProcessRunner {

  ProcessResult run(ProcessRequest request);
}
