package com.accord.sia.core.persistence;

import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Serializa toda escrita do SIA em uma unica thread.
 *
 * <p>SQLite aceita apenas um escritor por vez. Em vez de espalhar {@code busy_timeout} e retry por
 * todo lado, as escritas entram numa fila unica: leitura continua concorrente, escrita nunca
 * disputa lock com ela mesma.
 */
public final class SingleWriter implements AutoCloseable {

  private static final int ATTEMPTS = 3;
  private static final long RETRY_DELAY_MILLIS = 50L;

  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(
          Thread.ofPlatform().daemon(true).name("sia-db-writer").factory());

  @FunctionalInterface
  public interface SqlWork<T> {
    T run() throws SQLException;
  }

  public <T> T write(SqlWork<T> work) {
    try {
      return executor.submit(() -> attempt(work)).get(30, TimeUnit.SECONDS);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new DataAccessException("escrita interrompida", error);
    } catch (ExecutionException | TimeoutException error) {
      Throwable cause = error.getCause() == null ? error : error.getCause();
      if (cause instanceof DataAccessException dataAccess) {
        throw dataAccess;
      }
      throw new DataAccessException("falha ao escrever no banco", cause);
    }
  }

  public void writeVoid(SqlWork<Void> work) {
    write(work);
  }

  private static <T> T attempt(SqlWork<T> work) throws SQLException {
    SQLException last = null;
    for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
      try {
        return work.run();
      } catch (SQLException error) {
        last = error;
        if (!isBusy(error) || attempt == ATTEMPTS) {
          throw error;
        }
        sleepBeforeRetry();
      }
    }
    throw last;
  }

  private static boolean isBusy(SQLException error) {
    String message = error.getMessage() == null ? "" : error.getMessage().toUpperCase();
    return error.getErrorCode() == 5
        || message.contains("SQLITE_BUSY")
        || message.contains("LOCKED");
  }

  private static void sleepBeforeRetry() {
    try {
      Thread.sleep(RETRY_DELAY_MILLIS);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void close() {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      executor.shutdownNow();
    }
  }
}
