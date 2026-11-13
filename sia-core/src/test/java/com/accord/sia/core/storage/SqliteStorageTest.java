package com.accord.sia.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.accord.sia.core.persistence.Database;
import com.accord.sia.core.persistence.SchemaMigrator;
import com.accord.sia.core.persistence.SingleWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteStorageTest {

  @TempDir Path temp;

  private Database database;
  private SingleWriter writer;
  private SqliteStorage alpha;
  private SqliteStorage beta;

  @BeforeEach
  void setUp() throws IOException {
    database = Database.open(temp.resolve("sia.db"));
    new SchemaMigrator().migrate(database);
    writer = new SingleWriter();
    alpha = new SqliteStorage(database, writer, "alpha-plugin", Clock.systemUTC());
    beta = new SqliteStorage(database, writer, "beta-plugin", Clock.systemUTC());
  }

  @AfterEach
  void tearDown() {
    writer.close();
  }

  @Test
  void gravaELeValor() {
    alpha.put("ultimo.email", "42");

    assertThat(alpha.get("ultimo.email")).contains("42");
    assertThat(alpha.get("inexistente")).isEmpty();
  }

  @Test
  void sobrescreveValorExistente() {
    alpha.put("contador", "1");
    alpha.put("contador", "2");

    assertThat(alpha.get("contador")).contains("2");
  }

  @Test
  void removeChave() {
    alpha.put("chave", "valor");
    alpha.delete("chave");

    assertThat(alpha.get("chave")).isEmpty();
  }

  @Test
  void listaPorPrefixo() {
    alpha.put("sped.2024", "a");
    alpha.put("sped.2025", "b");
    alpha.put("outro", "c");

    assertThat(alpha.keys("sped")).containsExactly("sped.2024", "sped.2025");
    assertThat(alpha.keys("")).hasSize(3);
  }

  @Test
  void pluginsNaoEnxergamChavesUmDoOutro() {
    alpha.put("segredo", "do-alpha");
    beta.put("segredo", "do-beta");

    assertThat(alpha.get("segredo")).contains("do-alpha");
    assertThat(beta.get("segredo")).contains("do-beta");
    assertThat(beta.keys("")).containsExactly("segredo");
  }

  @Test
  void rejeitaChaveComCaminho() {
    assertThatThrownBy(() -> alpha.put("../fora", "x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> alpha.put("a/b", "x")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> alpha.put("a\\b", "x")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> alpha.put("   ", "x")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aceitaEscritaConcorrenteSemBusy() throws Exception {
    int writes = 200;
    int threads = 10;
    ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor();
    CountDownLatch start = new CountDownLatch(1);
    List<Throwable> failures = new ArrayList<>();
    try (callers) {
      for (int thread = 0; thread < threads; thread++) {
        int slice = thread;
        callers.submit(() -> {
          start.await();
          for (int index = 0; index < writes / threads; index++) {
            try {
              alpha.put("chave." + slice + "." + index, "valor-" + index);
            } catch (RuntimeException error) {
              synchronized (failures) {
                failures.add(error);
              }
            }
          }
          return null;
        });
      }
      start.countDown();
      callers.shutdown();
      assertThat(callers.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
    }

    assertThat(failures).isEmpty();
    assertThat(alpha.keys("chave.")).hasSize(writes);
  }

  @Test
  void valorGrandeSobrevive() {
    String payload = "x".repeat(50_000);

    alpha.put("grande", payload);

    assertThat(alpha.get("grande")).contains(payload);
  }
}
