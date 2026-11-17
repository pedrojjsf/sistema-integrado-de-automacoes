package com.accord.sia.core.schedule;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Duration;

/** Quando um agendamento dispara. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = Trigger.Cron.class, name = "cron"),
  @JsonSubTypes.Type(value = Trigger.Interval.class, name = "interval"),
  @JsonSubTypes.Type(value = Trigger.Event.class, name = "event")
})
public sealed interface Trigger permits Trigger.Cron, Trigger.Interval, Trigger.Event {

  /**
   * Expressao cron de 6 campos (segundos primeiro), com fuso.
   *
   * @param expression ex.: {@code 0 0 3 * * *} para 03:00 todo dia
   * @param zone fuso IANA; nulo usa o fuso da JVM
   */
  record Cron(String expression, String zone) implements Trigger {

    public Cron {
      if (expression == null || expression.isBlank()) {
        throw new IllegalArgumentException("expressao cron obrigatoria");
      }
    }

    public Cron(String expression) {
      this(expression, null);
    }
  }

  /** A cada intervalo fixo, contado a partir do termino do disparo anterior. */
  record Interval(Duration every) implements Trigger {

    public Interval {
      if (every == null || every.isNegative() || every.isZero()) {
        throw new IllegalArgumentException("intervalo precisa ser positivo");
      }
      if (every.toSeconds() < 1) {
        throw new IllegalArgumentException("intervalo minimo de 1 segundo");
      }
    }

    public Interval(long seconds) {
      this(Duration.ofSeconds(seconds));
    }
  }

  /** Dispara quando um evento com este tipo e publicado no barramento. */
  record Event(String type) implements Trigger {

    public Event {
      if (type == null || type.isBlank()) {
        throw new IllegalArgumentException("tipo de evento obrigatorio");
      }
    }
  }
}
