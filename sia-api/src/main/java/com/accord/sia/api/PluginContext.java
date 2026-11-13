package com.accord.sia.api;

import java.time.Clock;
import java.util.Objects;

/**
 * Servicos do Core entregues ao plugin na inicializacao.
 *
 * <p>Cada fachada checa permissao na chamada. O plugin nao implementa log, armazenamento nem
 * comunicacao: usa o que o Core oferece, e o que ele nao permitiu vira {@link
 * PermissionDeniedException}.
 *
 * @param pluginId identificador do plugin dono deste contexto
 * @param log log escopado ao plugin
 * @param config configuracao do plugin
 * @param storage armazenamento namespaced por plugin
 * @param events barramento de eventos do Core
 * @param secrets acesso a segredos, escopado por nome
 * @param processes execucao de processos externos pela allowlist do Core
 * @param clock relogio do Core (para testes deterministas)
 */
public record PluginContext(
    String pluginId,
    Log log,
    Configuration config,
    Storage storage,
    EventPublisher events,
    SecretAccess secrets,
    ProcessRunner processes,
    Clock clock) {

  public PluginContext {
    Objects.requireNonNull(pluginId, "pluginId");
    Objects.requireNonNull(log, "log");
    Objects.requireNonNull(config, "config");
    Objects.requireNonNull(storage, "storage");
    Objects.requireNonNull(events, "events");
    Objects.requireNonNull(secrets, "secrets");
    Objects.requireNonNull(processes, "processes");
    Objects.requireNonNull(clock, "clock");
  }
}
