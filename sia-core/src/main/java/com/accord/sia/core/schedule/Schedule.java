package com.accord.sia.core.schedule;

import java.time.Instant;
import java.util.Map;

/**
 * Agendamento persistido.
 *
 * @param id identificador
 * @param trigger quando dispara
 * @param target automacao a executar
 * @param params parametros fixos; valores sensiveis nao sao aceitos
 * @param enabled se esta ativo
 * @param nextFireAt proximo disparo (nulo para gatilho de evento)
 * @param owner {@code sub} de quem criou; vira {@code onBehalfOf} no run
 */
public record Schedule(
    String id,
    Trigger trigger,
    String target,
    Map<String, String> params,
    boolean enabled,
    Instant nextFireAt,
    String owner) {

  public Schedule {
    params = params == null ? Map.of() : Map.copyOf(params);
  }

  public Schedule withNextFireAt(Instant next) {
    return new Schedule(id, trigger, target, params, enabled, next, owner);
  }

  public Schedule withEnabled(boolean newEnabled) {
    return new Schedule(id, trigger, target, params, newEnabled, nextFireAt, owner);
  }
}
