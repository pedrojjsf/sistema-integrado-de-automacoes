package com.accord.sia.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Lock de instancia unica por banco.
 *
 * <p>SQLite aceita um escritor, e o desenho do SIA e "um dono do banco". O lock transforma isso em
 * regra verificada: um segundo daemon apontando para o mesmo banco se recusa a subir.
 *
 * <p>O lock e <b>do sistema operacional</b> ({@link FileLock}), nao do PID: o SO libera o lock
 * quando o processo morre, e um PID reaproveitado por outro programa nao bloqueia a subida.
 *
 * <p>Sao dois arquivos, de proposito:
 *
 * <ul>
 *   <li>{@code sia.db.lock} — mutex travado pelo SO; fica vazio e ilegivel para outros processos;
 *   <li>{@code sia.db.lock.pid} — texto com o PID, para diagnostico e para o script de parada.
 * </ul>
 */
public final class InstanceLock implements AutoCloseable {

  private final Path file;
  private final Path pidFile;
  private final FileChannel channel;
  private final FileLock lock;

  private InstanceLock(Path file, Path pidFile, FileChannel channel, FileLock lock) {
    this.file = file;
    this.pidFile = pidFile;
    this.channel = channel;
    this.lock = lock;
  }

  public static InstanceLock acquire(Path databasePath) throws IOException {
    Path absolute = databasePath.toAbsolutePath();
    Path lockFile = Path.of(absolute + ".lock");
    Path pidFile = Path.of(absolute + ".lock.pid");
    if (lockFile.getParent() != null) {
      Files.createDirectories(lockFile.getParent());
    }

    FileChannel channel =
        FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    FileLock lock;
    try {
      lock = channel.tryLock();
    } catch (OverlappingFileLockException sameJvm) {
      lock = null;
    } catch (IOException error) {
      channel.close();
      throw error;
    }

    if (lock == null) {
      String holder = readHolder(pidFile);
      channel.close();
      throw new IllegalStateException(
          "ja existe um SIA rodando para este banco"
              + (holder.isBlank() ? "" : " (pid " + holder + ")"));
    }

    long pid = ProcessHandle.current().pid();
    Files.writeString(pidFile, Long.toString(pid), StandardCharsets.UTF_8);
    channel.truncate(0);
    channel.write(ByteBuffer.wrap(Long.toString(pid).getBytes(StandardCharsets.UTF_8)));
    channel.force(true);
    return new InstanceLock(lockFile, pidFile, channel, lock);
  }

  private static String readHolder(Path pidFile) {
    try {
      return Files.readString(pidFile, StandardCharsets.UTF_8).trim();
    } catch (IOException ignored) {
      return "";
    }
  }

  /** Arquivo de mutex (vazio, travado pelo SO). */
  public Path file() {
    return file;
  }

  /** Arquivo legivel com o PID do dono. */
  public Path pidFile() {
    return pidFile;
  }

  @Override
  public void close() {
    try {
      Files.deleteIfExists(pidFile);
    } catch (IOException ignored) {
      // melhor esforco
    }
    try {
      lock.release();
    } catch (IOException ignored) {
      // lock ja liberado pelo SO
    }
    try {
      channel.close();
    } catch (IOException ignored) {
      // canal ja fechado
    }
    try {
      Files.deleteIfExists(file);
    } catch (IOException ignored) {
      // a proxima subida sobrescreve o arquivo
    }
  }
}
