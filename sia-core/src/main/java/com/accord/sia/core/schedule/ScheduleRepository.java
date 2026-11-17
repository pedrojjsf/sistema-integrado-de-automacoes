package com.accord.sia.core.schedule;

import com.accord.sia.api.RunId;
import com.accord.sia.core.persistence.DataAccessException;
import com.accord.sia.core.persistence.Database;
import com.accord.sia.core.persistence.SingleWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persistencia de agendamentos.
 *
 * <p>{@code schedule_fire} tem chave primaria composta {@code (schedule_id, fire_time)}: e o que
 * impede o mesmo horario de disparar duas vezes depois de um restart.
 */
public final class ScheduleRepository {

  private static final ObjectMapper JSON =
      new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

  private final Database database;
  private final SingleWriter writer;
  private final Clock clock;

  public ScheduleRepository(Database database, SingleWriter writer, Clock clock) {
    this.database = database;
    this.writer = writer;
    this.clock = clock;
  }

  public void save(Schedule schedule) {
    String sql =
        """
        INSERT INTO schedule (id, trigger, target, enabled, next_fire_at, owner, params)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            trigger = excluded.trigger,
            target = excluded.target,
            enabled = excluded.enabled,
            next_fire_at = excluded.next_fire_at,
            params = excluded.params
        """;
    writer.writeVoid(() -> {
      try (Connection connection = database.connection();
          PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, schedule.id());
        statement.setString(2, json(schedule.trigger()));
        statement.setString(3, schedule.target());
        statement.setInt(4, schedule.enabled() ? 1 : 0);
        setNullableLong(statement, 5, schedule.nextFireAt());
        statement.setString(6, schedule.owner());
        statement.setString(7, json(schedule.params()));
        statement.executeUpdate();
      }
      return null;
    });
  }

  public Optional<Schedule> find(String id) {
    String sql = SELECT + " WHERE id = ?";
    try (Connection connection = database.connection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, id);
      try (ResultSet result = statement.executeQuery()) {
        return result.next() ? Optional.of(map(result)) : Optional.empty();
      }
    } catch (SQLException error) {
      throw new DataAccessException("falha ao ler agendamento " + id, error);
    }
  }

  public List<Schedule> list() {
    String sql = SELECT + " ORDER BY id";
    try (Connection connection = database.connection();
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet result = statement.executeQuery()) {
      List<Schedule> schedules = new ArrayList<>();
      while (result.next()) {
        schedules.add(map(result));
      }
      return List.copyOf(schedules);
    } catch (SQLException error) {
      throw new DataAccessException("falha ao listar agendamentos", error);
    }
  }

  public void delete(String id) {
    writer.writeVoid(() -> {
      try (Connection connection = database.connection();
          PreparedStatement statement =
              connection.prepareStatement("DELETE FROM schedule WHERE id = ?")) {
        statement.setString(1, id);
        statement.executeUpdate();
      }
      try (Connection connection = database.connection();
          PreparedStatement statement =
              connection.prepareStatement("DELETE FROM schedule_fire WHERE schedule_id = ?")) {
        statement.setString(1, id);
        statement.executeUpdate();
      }
      return null;
    });
  }

  public void updateNextFire(String id, Instant next) {
    writer.writeVoid(() -> {
      try (Connection connection = database.connection();
          PreparedStatement statement =
              connection.prepareStatement("UPDATE schedule SET next_fire_at = ? WHERE id = ?")) {
        setNullableLong(statement, 1, next);
        statement.setString(2, id);
        statement.executeUpdate();
      }
      return null;
    });
  }

  /**
   * Registra um disparo.
   *
   * @return {@code false} quando aquele horario ja disparou (restart ou dupla chamada)
   */
  public boolean recordFire(String scheduleId, Instant fireTime) {
    long now = clock.millis();
    return writer.write(() -> {
      try (Connection connection = database.connection();
          PreparedStatement statement =
              connection.prepareStatement(
                  "INSERT OR IGNORE INTO schedule_fire (schedule_id, fire_time, run_id) "
                      + "VALUES (?, ?, ?)")) {
        statement.setString(1, scheduleId);
        statement.setLong(2, fireTime.toEpochMilli());
        statement.setString(3, RunId.generate().value());
        return statement.executeUpdate() > 0;
      }
    });
  }

  public boolean wasFired(String scheduleId, Instant fireTime) {
    String sql = "SELECT 1 FROM schedule_fire WHERE schedule_id = ? AND fire_time = ?";
    try (Connection connection = database.connection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, scheduleId);
      statement.setLong(2, fireTime.toEpochMilli());
      try (ResultSet result = statement.executeQuery()) {
        return result.next();
      }
    } catch (SQLException error) {
      throw new DataAccessException("falha ao consultar disparo", error);
    }
  }

  private static void setNullableLong(PreparedStatement statement, int index, Instant instant)
      throws SQLException {
    if (instant == null) {
      statement.setNull(index, java.sql.Types.INTEGER);
    } else {
      statement.setLong(index, instant.toEpochMilli());
    }
  }

  private static final String SELECT =
      "SELECT id, trigger, target, enabled, next_fire_at, owner, params FROM schedule";

  private static Schedule map(ResultSet result) throws SQLException {
    long next = result.getLong("next_fire_at");
    return new Schedule(
        result.getString("id"),
        trigger(result.getString("trigger")),
        result.getString("target"),
        params(result.getString("params")),
        result.getInt("enabled") == 1,
        result.wasNull() || next == 0 ? null : Instant.ofEpochMilli(next),
        result.getString("owner"));
  }

  private static Trigger trigger(String value) {
    try {
      return JSON.readValue(value, Trigger.class);
    } catch (JsonProcessingException error) {
      throw new DataAccessException("trigger invalido no banco", error);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, String> params(String value) {
    if (value == null || value.isBlank()) {
      return Map.of();
    }
    try {
      Map<String, Object> raw = JSON.readValue(value, Map.class);
      Map<String, String> params = new LinkedHashMap<>();
      raw.forEach((key, item) -> params.put(key, item == null ? "" : String.valueOf(item)));
      return Map.copyOf(params);
    } catch (JsonProcessingException error) {
      throw new DataAccessException("params invalidos no banco", error);
    }
  }

  private static String json(Object value) {
    try {
      return JSON.writeValueAsString(value);
    } catch (JsonProcessingException error) {
      throw new IllegalArgumentException("nao foi possivel serializar " + value, error);
    }
  }
}
