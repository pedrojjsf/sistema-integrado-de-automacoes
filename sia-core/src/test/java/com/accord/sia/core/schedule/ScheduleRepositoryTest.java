package com.accord.sia.core.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.accord.sia.core.persistence.Database;
import com.accord.sia.core.persistence.SchemaMigrator;
import com.accord.sia.core.persistence.SingleWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScheduleRepositoryTest {

  @TempDir Path temp;

  private SingleWriter writer;
  private ScheduleRepository repository;

  @BeforeEach
  void setUp() throws IOException {
    Database database = Database.open(temp.resolve("sia.db"));
    new SchemaMigrator().migrate(database);
    writer = new SingleWriter();
    repository = new ScheduleRepository(database, writer, Clock.systemUTC());
  }

  @AfterEach
  void tearDown() {
    writer.close();
  }

  @Test
  void gravaELeAgendamentoComTriggerEParams() {
    Instant next = Instant.now().plusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    repository.save(
        new Schedule(
            "s1",
            new Trigger.Interval(Duration.ofSeconds(30)),
            "alpha.run",
            Map.of("arquivo", "cnpjs.txt"),
            true,
            next,
            "sub-1"));

    Schedule loaded = repository.find("s1").orElseThrow();

    assertThat(loaded.target()).isEqualTo("alpha.run");
    assertThat(loaded.params()).containsEntry("arquivo", "cnpjs.txt");
    assertThat(loaded.trigger()).isInstanceOf(Trigger.Interval.class);
    assertThat(loaded.nextFireAt()).isEqualTo(next);
    assertThat(loaded.enabled()).isTrue();
  }

  @Test
  void preservaTriggerCronEEventoNaSerializacao() {
    repository.save(
        new Schedule(
            "cron",
            new Trigger.Cron("0 0 3 * * *", "America/Sao_Paulo"),
            "a.b",
            Map.of(),
            true,
            null,
            "o"));
    repository.save(
        new Schedule(
            "evt", new Trigger.Event("arquivo.baixado"), "a.c", Map.of(), true, null, "o"));

    assertThat(repository.find("cron").orElseThrow().trigger())
        .isEqualTo(new Trigger.Cron("0 0 3 * * *", "America/Sao_Paulo"));
    assertThat(repository.find("evt").orElseThrow().trigger())
        .isEqualTo(new Trigger.Event("arquivo.baixado"));
  }

  @Test
  void registroDeDisparoEhIdempotentePorHorario() {
    repository.save(new Schedule("s1", new Trigger.Interval(60), "a.b", Map.of(), true, null, "o"));
    Instant fireTime = Instant.now();

    assertThat(repository.recordFire("s1", fireTime)).isTrue();
    assertThat(repository.recordFire("s1", fireTime)).isFalse();

    assertThat(repository.wasFired("s1", fireTime)).isTrue();
    assertThat(repository.wasFired("s1", fireTime.plusSeconds(1))).isFalse();
  }

  @Test
  void removeAgendamentoEDisparos() {
    repository.save(new Schedule("s1", new Trigger.Interval(60), "a.b", Map.of(), true, null, "o"));
    repository.recordFire("s1", Instant.now());

    repository.delete("s1");

    assertThat(repository.find("s1")).isEmpty();
    assertThat(repository.list()).isEmpty();
  }

  @Test
  void atualizaProximoDisparo() {
    repository.save(new Schedule("s1", new Trigger.Interval(60), "a.b", Map.of(), true, null, "o"));
    Instant next = Instant.now().plusSeconds(120).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);

    repository.updateNextFire("s1", next);

    assertThat(repository.find("s1").orElseThrow().nextFireAt()).isEqualTo(next);
  }
}
