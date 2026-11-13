package com.accord.sia.core.run;

import com.accord.sia.api.Actor;
import com.accord.sia.api.RunId;
import com.accord.sia.core.persistence.DataAccessException;
import com.accord.sia.core.persistence.Database;
import com.accord.sia.core.persistence.SingleWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Persistencia de execucoes e do log de execucao.
 *
 * <p>Regra da casa: parametro entra como <b>nome</b>, saida entra como <b>chave</b>. Valor nenhum.
 */
public final class RunRepository {

  private final Database database;
  private final SingleWriter writer;
  private final Clock clock;

  public RunRepository(Database database, SingleWriter writer, Clock clock) {
    this.database = database;
    this.writer = writer;
    this.clock = clock;
  }

  public RunId start(
      String pluginId,
      String automationId,
      Actor actor,
      String trigger,
      Collection<String> paramsKeys) {
    RunId runId = RunId.generate();
    long now = clock.millis();
    String sql =
        """
        INSERT INTO run (id, actor, actor_name, on_behalf_of, plugin_id, automation_id, status,
                         started_at, trigger, params_keys)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    writer.writeVoid(() -> {
      try (Connection connection = database.connection();
          PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, runId.value());
        statement.setString(2, actor.subject());
        statement.setString(3, actor.displayName());
        statement.setString(4, actor.onBehalfOf());
        statement.setString(5, pluginId);
        statement.setString(6, automationId);
        statement.setString(7, RunStatus.RUNNING.name());
        statement.setLong(8, now);
        statement.setString(9, trigger);
        statement.setString(10, join(paramsKeys));
        statement.executeUpdate();
      }
      return null;
    });
    return runId;
  }

  public void finish(
      RunId runId,
      RunStatus status,
      String errorCode,
      String message,
      Collection<String> outputsKeys) {
    long now = clock.millis();
    String sql =
        "UPDATE run SET status = ?, finished_at = ?, error_code = ?, message = ?, outputs_keys = ? WHERE id = ?";
    writer.writeVoid(() -> {
      try (Connection connection = database.connection();
          PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, status.name());
        statement.setLong(2, now);
        statement.setString(3, errorCode);
        statement.setString(4, message);
        statement.setString(5, join(outputsKeys));
        statement.setString(6, runId.value());
        statement.executeUpdate();
      }
      return null;
    });
  }

  public void appendLog(RunId runId, String level, String eventCode, String message) {
    long now = clock.millis();
    String sql =
        "INSERT INTO run_log (run_id, ts, level, event_code, message) VALUES (?, ?, ?, ?, ?)";
    writer.writeVoid(() -> {
      try (Connection connection = database.connection();
          PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, runId.value());
        statement.setLong(2, now);
        statement.setString(3, level);
        statement.setString(4, eventCode);
        statement.setString(5, message);
        statement.executeUpdate();
      }
      return null;
    });
  }

  public Optional<RunRecord> find(RunId runId) {
    String sql = SELECT + " WHERE id = ?";
    try (Connection connection = database.connection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, runId.value());
      try (ResultSet result = statement.executeQuery()) {
        return result.next() ? Optional.of(map(result)) : Optional.empty();
      }
    } catch (SQLException error) {
      throw new DataAccessException("falha ao ler execucao " + runId, error);
    }
  }

  public List<RunRecord> recent(int limit) {
    String sql = SELECT + " ORDER BY started_at DESC LIMIT ?";
    try (Connection connection = database.connection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setInt(1, Math.max(1, limit));
      List<RunRecord> records = new ArrayList<>();
      try (ResultSet result = statement.executeQuery()) {
        while (result.next()) {
          records.add(map(result));
        }
      }
      return List.copyOf(records);
    } catch (SQLException error) {
      throw new DataAccessException("falha ao listar execucoes", error);
    }
  }

  public List<String> logs(RunId runId) {
    String sql = "SELECT level, message FROM run_log WHERE run_id = ? ORDER BY id";
    try (Connection connection = database.connection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, runId.value());
      List<String> lines = new ArrayList<>();
      try (ResultSet result = statement.executeQuery()) {
        while (result.next()) {
          lines.add(result.getString(1) + " " + result.getString(2));
        }
      }
      return List.copyOf(lines);
    } catch (SQLException error) {
      throw new DataAccessException("falha ao ler log da execucao", error);
    }
  }

  private static final String SELECT =
      """
      SELECT id, plugin_id, automation_id, actor, actor_name, on_behalf_of, status, started_at,
             finished_at, trigger, error_code, message, params_keys, outputs_keys
      FROM run
      """;

  private static RunRecord map(ResultSet result) throws SQLException {
    long finished = result.getLong("finished_at");
    return new RunRecord(
        RunId.of(result.getString("id")),
        result.getString("plugin_id"),
        result.getString("automation_id"),
        result.getString("actor"),
        result.getString("actor_name"),
        result.getString("on_behalf_of"),
        RunStatus.fromWire(result.getString("status")),
        Instant.ofEpochMilli(result.getLong("started_at")),
        result.wasNull() || finished == 0 ? null : Instant.ofEpochMilli(finished),
        result.getString("trigger"),
        result.getString("error_code"),
        result.getString("message"),
        split(result.getString("params_keys")),
        split(result.getString("outputs_keys")));
  }

  private static String join(Collection<String> values) {
    return values == null ? "" : String.join(",", values);
  }

  private static List<String> split(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return List.of(value.split(","));
  }
}
