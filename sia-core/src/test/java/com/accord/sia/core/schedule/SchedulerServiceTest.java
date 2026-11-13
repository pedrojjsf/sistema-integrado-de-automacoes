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

    Thread.sleep(2_600);

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
