package com.accord.sia.api;

import java.time.Clock;
import java.util.Objects;

/**
 * Dados e servicos de uma execucao especifica.
 *
 * @param runId identificador da execucao
 * @param pluginId plugin dono da automacao
 * @param automationId automacao em execucao
 * @param actor quem disparou (usuario, scheduler ou workflow)
 * @param params parametros recebidos
 * @param log log escopado a execucao
 * @param progress canal de progresso (vira evento no daemon)
 * @param cancellation sinal de cancelamento cooperativo
 * @param storage armazenamento namespaced do plugin
 * @param events publicacao de eventos
 * @param secrets leitura de segredos
 * @param processes execucao de processos externos pela allowlist do Core
 * @param clock relogio do Core
 */
public record AutomationContext(
    RunId runId,
    String pluginId,
    String automationId,
    Actor actor,
    Parameters params,
    Log log,
    ProgressSink progress,
    CancellationToken cancellation,
    Storage storage,
    EventPublisher events,
    SecretAccess secrets,
    ProcessRunner processes,
    Clock clock) {

  public AutomationContext {
    Objects.requireNonNull(runId, "runId");
    Objects.requireNonNull(pluginId, "pluginId");
    Objects.requireNonNull(automationId, "automationId");
    Objects.requireNonNull(actor, "actor");
    Objects.requireNonNull(params, "params");
    Objects.requireNonNull(log, "log");
    Objects.requireNonNull(progress, "progress");
    Objects.requireNonNull(cancellation, "cancellation");
    Objects.requireNonNull(storage, "storage");
    Objects.requireNonNull(events, "events");
    Objects.requireNonNull(secrets, "secrets");
    Objects.requireNonNull(processes, "processes");
    Objects.requireNonNull(clock, "clock");
  }
}
