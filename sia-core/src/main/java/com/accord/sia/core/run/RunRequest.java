package com.accord.sia.core.run;

import com.accord.sia.api.Actor;
import com.accord.sia.api.Parameters;
import java.time.Duration;
import java.util.Objects;

/**
 * Pedido de execucao de uma automacao.
 *
 * @param automationId id qualificado (ex.: {@code demo.lote})
 * @param params parametros informados pelo solicitante
 * @param actor quem pediu (usuario, scheduler ou workflow)
 * @param trigger origem do disparo ({@code manual}, {@code schedule}, {@code workflow}, {@code
 *     event})
 * @param timeout limite de execucao; nulo usa o padrao do engine
 */
public record RunRequest(
    String automationId, Parameters params, Actor actor, String trigger, Duration timeout) {

  public static final String TRIGGER_MANUAL = "manual";

  public RunRequest {
    Objects.requireNonNull(automationId, "automationId");
    Objects.requireNonNull(actor, "actor");
    params = params == null ? Parameters.empty() : params;
    trigger = trigger == null || trigger.isBlank() ? TRIGGER_MANUAL : trigger;
  }

  public static RunRequest manual(String automationId, Parameters params, Actor actor) {
    return new RunRequest(automationId, params, actor, TRIGGER_MANUAL, null);
  }

  public RunRequest withTimeout(Duration newTimeout) {
    return new RunRequest(automationId, params, actor, trigger, newTimeout);
  }

  public RunRequest withTrigger(String newTrigger) {
    return new RunRequest(automationId, params, actor, newTrigger, timeout);
  }
}
