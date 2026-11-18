package com.accord.sia.core.schedule;

import com.accord.sia.core.event.InProcessEventBus;
import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Agenda execucoes.
 *
 * <p>Fica desacoplado do engine de proposito: o scheduler so decide <b>quando</b>; quem executa e o
 * engine (via {@link Launcher}). Duas garantias contra duplicidade:
 *
 * <ul>
 *   <li>{@code schedule_fire} tem chave unica por horario — o mesmo instante nao dispara duas vezes
 *       depois de um restart;
 *   <li>na subida, horario perdido e <b>pulado</b> (politica {@code skip}), nunca recuperado em
 *       rajada.
 * </ul>
 */
public final class SchedulerService implements AutoCloseable {

  /** Executa a automacao agendada; implementado pelo Core. */
  @FunctionalInterface
  public interface Launcher {
    void launch(Schedule schedule, Instant fireTime);
  }

  private final ScheduleRepository repository;
  private final Launcher launcher;
  private final InProcessEventBus events;
  private final Clock clock;
  private final ZoneId zone;
  private final ScheduledExecutorService timer =
      Executors.newScheduledThreadPool(
          1, Thread.ofPlatform().daemon(true).name("sia-scheduler").factory());
  private final Map<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
  private final Map<String, AutoCloseable> subscriptions = new ConcurrentHashMap<>();
  private final AtomicInteger fired = new AtomicInteger();

  public SchedulerService(
      ScheduleRepository repository, Launcher launcher, InProcessEventBus events, Clock clock) {
    this(repository, launcher, events, clock, ZoneId.systemDefault());
  }

  public SchedulerService(
      ScheduleRepository repository,
      Launcher launcher,
      InProcessEventBus events,
      Clock clock,
      ZoneId zone) {
    this.repository = repository;
    this.launcher = launcher;
    this.events = events;
    this.clock = clock;
    this.zone = zone == null ? ZoneId.systemDefault() : zone;
  }

  /** Carrega os agendamentos existentes e arma os relogios. */
  public synchronized void start() {
    for (Schedule schedule : repository.list()) {
      if (!schedule.enabled()) {
        continue;
      }
      if (schedule.trigger() instanceof Trigger.Event event) {
        subscribe(schedule, event);
      } else {
        Instant next = nextAfter(schedule.trigger(), clock.instant(), clock.instant());
        repository.updateNextFire(schedule.id(), next);
        arm(schedule.withNextFireAt(next));
      }
    }
  }

  public synchronized Schedule create(
      String target, Trigger trigger, Map<String, String> params, String owner) {
    String id = UUID.randomUUID().toString();
    Instant next =
        trigger instanceof Trigger.Event
            ? null
            : nextAfter(trigger, clock.instant(), clock.instant());
    Schedule schedule = new Schedule(id, trigger, target, params, true, next, owner);
    repository.save(schedule);
    if (trigger instanceof Trigger.Event event) {
      subscribe(schedule, event);
    } else {
      arm(schedule);
    }
    return schedule;
  }

  public synchronized void setEnabled(String id, boolean enabled) {
    Schedule schedule =
        repository
            .find(id)
            .orElseThrow(() -> new IllegalArgumentException("agendamento desconhecido: " + id));
    cancel(id);
    if (!enabled) {
      repository.save(schedule.withEnabled(false));
      return;
    }
    Instant next =
        schedule.trigger() instanceof Trigger.Event
            ? null
            : nextAfter(schedule.trigger(), clock.instant(), clock.instant());
    repository.save(schedule.withEnabled(true).withNextFireAt(next));
    if (schedule.trigger() instanceof Trigger.Event event) {
      subscribe(schedule, event);
    } else {
      arm(schedule.withNextFireAt(next));
    }
  }

  public synchronized boolean delete(String id) {
    cancel(id);
    if (repository.find(id).isEmpty()) {
      return false;
    }
    repository.delete(id);
    return true;
  }

  public List<Schedule> list() {
    return repository.list();
  }

  public int firedCount() {
    return fired.get();
  }

  private void subscribe(Schedule schedule, Trigger.Event event) {
    if (subscriptions.containsKey(schedule.id())) {
      return;
    }
    Consumer<Map<String, Object>> handler =
        payload -> {
          if (repository.recordFire(schedule.id(), clock.instant())) {
            fired.incrementAndGet();
            launcher.launch(schedule, clock.instant());
          }
        };
    subscriptions.put(schedule.id(), events.subscribe(event.type(), handler));
  }

  private void arm(Schedule schedule) {
    cancelPending(schedule.id());
    Instant next = schedule.nextFireAt();
    if (next == null) {
      return;
    }
    long delay = Math.max(0, Duration.between(clock.instant(), next).toMillis());
    pending.put(schedule.id(), timer.schedule(() -> fire(schedule), delay, TimeUnit.MILLISECONDS));
  }

  private void fire(Schedule armed) {
    Instant fireTime = armed.nextFireAt();
    Schedule current = repository.find(armed.id()).orElse(null);
    if (fireTime == null || current == null || !current.enabled()) {
      return;
    }
    if (repository.recordFire(armed.id(), fireTime)) {
      fired.incrementAndGet();
      launcher.launch(current, fireTime);
    }
    Instant next = nextAfter(current.trigger(), fireTime, clock.instant());
    repository.updateNextFire(armed.id(), next);
    arm(current.withNextFireAt(next));
  }

  /**
   * Proximo disparo depois de {@code reference}, mas sempre no futuro de {@code now}.
   *
   * <p>Se o horario ficou no passado (daemon desligado), pula para o proximo — sem rajada.
   */
  Instant nextAfter(Trigger trigger, Instant reference, Instant now) {
    Instant base = reference.isBefore(now) ? now : reference;
    return switch (trigger) {
      case Trigger.Interval interval -> {
        Instant next = base.plus(interval.every());
        while (next.isBefore(now)) {
          next = next.plus(interval.every());
        }
        yield next;
      }
      case Trigger.Cron cron -> nextCron(cron, base);
      case Trigger.Event ignored -> null;
    };
  }

  private Instant nextCron(Trigger.Cron trigger, Instant after) {
    // trigger.zone() e override explicito; sem ele vale o fuso configurado no SIA (Brasilia)
    ZoneId effective = trigger.zone() == null ? zone : ZoneId.of(trigger.zone());
    CronParser parser =
        new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ));
    Cron cron = parser.parse(normalize(trigger.expression())).validate();
    ExecutionTime execution = ExecutionTime.forCron(cron);
    ZonedDateTime from = ZonedDateTime.ofInstant(after, effective);
    Optional<ZonedDateTime> next = execution.nextExecution(from);
    return next.map(ZonedDateTime::toInstant).orElse(null);
  }

  /**
   * Quartz exige {@code ?} em um dos campos de dia. Como o usuario escreve {@code *} nos dois, o
   * Core normaliza em vez de recusar: {@code * * * * * *} vira {@code * * * * * ?}.
   */
  static String normalize(String expression) {
    String[] fields = expression.trim().split("\\s+");
    if (fields.length == 6 && "*".equals(fields[3]) && "*".equals(fields[5])) {
      fields[5] = "?";
      return String.join(" ", fields);
    }
    return expression.trim();
  }

  private void cancel(String id) {
    cancelPending(id);
    AutoCloseable subscription = subscriptions.remove(id);
    if (subscription != null) {
      try {
        subscription.close();
      } catch (Exception ignored) {
        // assinatura de evento idempotente
      }
    }
  }

  private void cancelPending(String id) {
    ScheduledFuture<?> future = pending.remove(id);
    if (future != null) {
      future.cancel(false);
    }
  }

  @Override
  public synchronized void close() {
    pending.keySet().forEach(this::cancel);
    timer.shutdownNow();
  }
}
