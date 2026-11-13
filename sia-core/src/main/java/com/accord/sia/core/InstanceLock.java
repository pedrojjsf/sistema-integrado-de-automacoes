package com.accord.sia.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Lock de instancia unica por banco.
 *
 * <p>SQLite aceita um escritor, e o desenho do SIA e "um dono do banco". O lock transforma isso em
 * regra verificada: um segundo daemon apontando para o mesmo banco se recusa a subir.
 */
public final class InstanceLock implements AutoCloseable {

  private final Path file;

  private InstanceLock(Path file) {
    this.file = file;
  }

  public static InstanceLock acquire(Path databasePath) throws IOException {
    Path lockFile = Path.of(databasePath.toAbsolutePath() + ".lock");
    if (Files.exists(lockFile)) {
      String content = Files.readString(lockFile, StandardCharsets.UTF_8).trim();
      Optional<ProcessHandle> holder = parse(content).flatMap(ProcessHandle::of);
      if (holder.map(ProcessHandle::isAlive).orElse(false)) {
        throw new IllegalStateException(
            "ja existe um SIA rodando para este banco (pid " + content + ")");
      }
    }
    if (lockFile.getParent() != null) {
      Files.createDirectories(lockFile.getParent());
    }
    Files.writeString(
        lockFile, Long.toString(ProcessHandle.current().pid()), StandardCharsets.UTF_8);
    return new InstanceLock(lockFile);
  }

  private static Optional<Long> parse(String content) {
    try {
      return Optional.of(Long.parseLong(content));
    } catch (NumberFormatException error) {
      return Optional.empty();
    }
  }

  public Path file() {
    return file;
  }

  @Override
  public void close() {
    try {
      Files.deleteIfExists(file);
    } catch (IOException ignored) {
      // lock de processo morto e sobrescrito na proxima subida
    }
  }
}
