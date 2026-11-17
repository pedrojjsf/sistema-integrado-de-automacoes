package com.accord.sia.core.run;

import com.accord.sia.api.RunId;
import com.accord.sia.core.persistence.DataAccessException;
import com.accord.sia.core.persistence.Database;
import com.accord.sia.core.persistence.SingleWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Optional;

/**
 * Chaves de idempotencia das chamadas {@code POST /api/runs}.
 *
 * <p>Retry de rede do cliente nao pode criar duas execucoes. O registro acontece <b>antes</b> de
 * disparar o run: quem chega primeiro grava a chave e executa; quem chega depois recebe o mesmo
 * {@code runId}.
 */
public final class IdempotencyRepository {

  private final Database database;
  private final SingleWriter writer;
  private final Clock clock;

  public IdempotencyRepository(Database database, SingleWriter writer, Clock clock) {
    this.database = database;
    this.writer = writer;
    this.clock = clock;
  }

  public Optional<RunId> find(String key) {
    String sql = "SELECT run_id FROM idempotency_key WHERE key = ?";
    try (Connection connection = database.connection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, key);
      try (ResultSet result = statement.executeQuery()) {
        return result.next() ? Optional.of(RunId.of(result.getString(1))) : Optional.empty();
      }
    } catch (SQLException error) {
      throw new DataAccessException("falha ao consultar chave de idempotencia", error);
    }
  }

  /**
   * @return {@code true} se esta chamada registrou a chave (e deve executar); {@code false} se
   *     outra chamada chegou primeiro
   */
  public boolean register(String key, RunId runId) {
    long now = clock.millis();
    String sql = "INSERT OR IGNORE INTO idempotency_key (key, run_id, created_at) VALUES (?, ?, ?)";
    return writer.write(() -> {
      try (Connection connection = database.connection();
          PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, key);
        statement.setString(2, runId.value());
        statement.setLong(3, now);
        return statement.executeUpdate() > 0;
      }
    });
  }

  public void forget(String key) {
    String sql = "DELETE FROM idempotency_key WHERE key = ?";
    writer.writeVoid(() -> {
      try (Connection connection = database.connection();
          PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, key);
        statement.executeUpdate();
      }
      return null;
    });
  }
}
