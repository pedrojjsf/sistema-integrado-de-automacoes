package com.accord.sia.core.plugin;

import com.accord.sia.core.persistence.DataAccessException;
import com.accord.sia.core.persistence.Database;
import com.accord.sia.core.persistence.SingleWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Estado persistido dos plugins (tabela {@code plugin_state}).
 *
 * <p>Sem isso, desabilitar um plugin duraria ate o proximo restart — o daemon reabilitava tudo o
 * que encontrava em {@code plugins/}.
 */
public final class PluginStateRepository {

  private final Database database;
  private final SingleWriter writer;
  private final Clock clock;

  public PluginStateRepository(Database database, SingleWriter writer, Clock clock) {
    this.database = database;
    this.writer = writer;
    this.clock = clock;
  }

  public void save(String pluginId, String version, String state, String error) {
    long now = clock.millis();
    String sql =
        """
        INSERT INTO plugin_state (id, version, state, loaded_at, error)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            version = excluded.version,
            state = excluded.state,
            loaded_at = excluded.loaded_at,
            error = excluded.error
        """;
    writer.writeVoid(() -> {
      try (Connection connection = database.connection();
          PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, pluginId);
        statement.setString(2, version);
        statement.setString(3, state);
        statement.setLong(4, now);
        statement.setString(5, error);
        statement.executeUpdate();
      }
      return null;
    });
  }

  public Optional<Row> find(String pluginId) {
    String sql = "SELECT id, version, state, loaded_at, error FROM plugin_state WHERE id = ?";
    try (Connection connection = database.connection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, pluginId);
      try (ResultSet result = statement.executeQuery()) {
        return result.next() ? Optional.of(map(result)) : Optional.empty();
      }
    } catch (SQLException error) {
      throw new DataAccessException("falha ao ler estado do plugin", error);
    }
  }

  public Map<String, Row> all() {
    String sql = "SELECT id, version, state, loaded_at, error FROM plugin_state ORDER BY id";
    try (Connection connection = database.connection();
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet result = statement.executeQuery()) {
      Map<String, Row> rows = new LinkedHashMap<>();
      while (result.next()) {
        Row row = map(result);
        rows.put(row.id(), row);
      }
      return Map.copyOf(rows);
    } catch (SQLException error) {
      throw new DataAccessException("falha ao listar estado dos plugins", error);
    }
  }

  public void delete(String pluginId) {
    writer.writeVoid(() -> {
      try (Connection connection = database.connection();
          PreparedStatement statement =
              connection.prepareStatement("DELETE FROM plugin_state WHERE id = ?")) {
        statement.setString(1, pluginId);
        statement.executeUpdate();
      }
      return null;
    });
  }

  private static Row map(ResultSet result) throws SQLException {
    return new Row(
        result.getString("id"),
        result.getString("version"),
        result.getString("state"),
        result.getString("error"),
        Instant.ofEpochMilli(result.getLong("loaded_at")));
  }

  /** Linha da tabela. */
  public record Row(String id, String version, String state, String error, Instant loadedAt) {}
}
