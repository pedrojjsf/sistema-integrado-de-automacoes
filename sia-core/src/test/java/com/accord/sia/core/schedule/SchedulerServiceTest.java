package com.accord.sia.core.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.accord.sia.core.event.InProcessEventBus;
import com.accord.sia.core.persistence.Database;
import com.accord.sia.core.persistence.SchemaMigrator;
import com.accord.sia.core.persistence.SingleWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchedulerServiceTest {

  @TempDir Path temp;

  private SingleWriter writer;
  private ScheduleRepository repository;
  private InProcessEventBus events;
  private SchedulerService scheduler;
  private List<Schedule> launches;

  @BeforeEach
  void setUp() throws IOException {
    Database database = Database.open(temp.resolve("sia.db"));
    new SchemaMigrator().migrate(database);
    writer = new SingleWriter();
    repository = new ScheduleRepository(database, writer, Clock.systemUTC());
    events = new InProcessEventBus();
    launches = new CopyOnWriteArrayList<>();
    scheduler =
        new SchedulerService(
            repository, (schedule, fireTime) -> launches.add(schedule), events, Clock.systemUTC());
  }

  @AfterEach
  void tearDown() {
    scheduler.close();
    writer.close();
  }

  @Test
  void intervaloDisparaMaisDeUmaVez() throws Exception {
    scheduler.create("alpha.run", new Trigger.Interval(Duration.ofSeconds(1)), Map.of(), "sub-1");
    scheduler.start();

    // espera de verdade (com limite) em vez de sleep fixo: sob carga o disparo atrasa
    long deadline = System.currentTimeMillis() + 10_000;
    while (launches.size() < 2 && System.currentTimeMillis() < deadline) {
      Thread.sleep(100);
    }

    assertThat(launches).hasSizeGreaterThanOrEqualTo(2);
    assertThat(scheduler.firedCount()).isGreaterThanOrEqualTo(2);
  }

  @Test
  void horarioPerdidoEhPuladoSemRajada() throws Exception {
    Instant past = Instant.now().minusSeconds(3_600);
    repository.save(
        new Schedule(
            "atrasado",
            new Trigger.Interval(Duration.ofSeconds(60)),
            "alpha.run",
            Map.of(),
            true,
            past,
            "sub-1"));

    scheduler.start();
    Thread.sleep(400);

    assertThat(launches).isEmpty();
    Instant next = repository.find("atrasado").orElseThrow().nextFireAt();
    assertThat(next).isAfter(Instant.now());
  }

  @Test
  void eventoDisparaExecucao() throws Exception {
    scheduler.create("alpha.run", new Trigger.Event("arquivo.baixado"), Map.of(), "sub-1");
    scheduler.start();

    events.publish("arquivo.baixado", Map.of("arquivo", "x.txt"));
    Thread.sleep(200);

    assertThat(launches).hasSize(1);
  }

  @Test
  void cronCalculaProximoDisparoNoFuturo() {
    Trigger.Cron cron = new Trigger.Cron("*/2 * * * * *");
    Instant now = Instant.now();

    Instant next = scheduler.nextAfter(cron, now, now);

    assertThat(next).isAfter(now).isBefore(now.plusSeconds(3));
  }

  @Test
  void cronUsaFusoDeBrasiliaPorPadrao() {
    SchedulerService brasilia =
        new SchedulerService(
            repository,
            (schedule, fireTime) -> launches.add(schedule),
            events,
            Clock.systemUTC(),
            java.time.ZoneId.of("America/Sao_Paulo"));

    // 03:00 em Brasilia (UTC-3) equivale a 06:00 UTC
    Instant next =
        brasilia.nextAfter(
            new Trigger.Cron("0 0 3 * * ?"),
            Instant.parse("2026-11-18T12:00:00Z"),
            Instant.parse("2026-11-18T12:00:00Z"));

    assertThat(next).isEqualTo(Instant.parse("2026-11-19T06:00:00Z"));
    brasilia.close();
  }

  @Test
  void zonaExplicitaNoTriggerSobrepoeOFusoPadrao() {
    Instant next =
        scheduler.nextAfter(
            new Trigger.Cron("0 0 3 * * ?", "UTC"),
            Instant.parse("2026-11-18T12:00:00Z"),
            Instant.parse("2026-11-18T12:00:00Z"));

    assertThat(next).isEqualTo(Instant.parse("2026-11-19T03:00:00Z"));
  }

  @Test
  void desabilitarCancelaProximoDisparo() throws Exception {
    Schedule schedule =
        scheduler.create(
            "alpha.run", new Trigger.Interval(Duration.ofSeconds(1)), Map.of(), "sub-1");
    scheduler.start();
    scheduler.setEnabled(schedule.id(), false);

    Thread.sleep(2_200);

    assertThat(launches).isEmpty();
    assertThat(repository.find(schedule.id()).orElseThrow().enabled()).isFalse();
  }

  @Test
  void removerAgendamentoImpedeDisparo() throws Exception {
    Schedule schedule =
        scheduler.create(
            "alpha.run", new Trigger.Interval(Duration.ofSeconds(1)), Map.of(), "sub-1");
    scheduler.start();

    assertThat(scheduler.delete(schedule.id())).isTrue();
    Thread.sleep(2_200);

    assertThat(launches).isEmpty();
  }
}
