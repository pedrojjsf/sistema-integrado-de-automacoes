package com.accord.sia.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstanceLockTest {

  @TempDir Path temp;

  @Test
  void segundoLockNoMesmoBancoEhRecusado() throws Exception {
    Path database = temp.resolve("sia.db");

    try (InstanceLock first = InstanceLock.acquire(database)) {
      assertThat(Files.readString(first.pidFile())).isNotBlank();
      assertThatThrownBy(() -> InstanceLock.acquire(database))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("ja existe um SIA rodando");
    }
  }

  @Test
  void lockEhRemovidoAoFechar() throws Exception {
    Path database = temp.resolve("sia.db");

    InstanceLock lock = InstanceLock.acquire(database);
    Path file = lock.file();
    lock.close();

    assertThat(Files.exists(file)).isFalse();
  }

  @Test
  void arquivoDeLockOrfaoComPidReaproveitadoNaoBloqueia() throws Exception {
    Path database = temp.resolve("sia.db");
    Path lockFile = Path.of(database.toAbsolutePath() + ".lock");
    // PID de outro programa (o proprio processo de teste) simulando um lock que sobrou
    Files.writeString(lockFile, Long.toString(ProcessHandle.current().pid()));

    try (InstanceLock acquired = InstanceLock.acquire(database)) {
      assertThat(Files.readString(acquired.pidFile()))
          .isEqualTo(Long.toString(ProcessHandle.current().pid()));
    }
  }
}
