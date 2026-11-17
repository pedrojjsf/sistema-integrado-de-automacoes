package com.accord.sia.core.workflow;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Persistencia de execucoes de workflow e do estado por no. */
public final class WorkflowRepository {

  private static final ObjectMapper JSON =
      new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

  private final Database database;
  private final SingleWriter writer;
  private final Clock clock;

  public WorkflowRepository(Database database, SingleWriter writer, Clock clock) {
    this.database = database;
    this.writer = writer;
    this.clock = clock;
  }

  public void createRun(String runId, String name, String definitionYaml, String inputsJson) {
    long now = clock.millis();
    String sql =
        """
        INSERT INTO workflow_run (id, name, status, started_at, definition, inputs)
        VALUES (?, ?, 'RUNNING', ?, ?, ?)
        """;
    writer.writeVoid(() -> {
      try (Connection connection = database.connection();
          PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, runId);
        statement.setString(2, name);
        statement.setLong(3, now);
        statement.setString(4, definitionYaml);
        statement.setString(5, inputsJson);
        statement.executeUpdate();
      }
      return null;
    });
  }

  public void finishRun(String runId, String status) {
    long now = clock.millis();
    writer.writeVoid(() -> {
      try (Connection connection = database.connection();
          PreparedStatement statement =
              connection.prepareStatement(
                  "UPDATE workflow_run SET status = ?, finished_at = ? WHERE id = ?")) {
        statement.setString(1, status);
        statement.setLong(2, now);
        statement.setString(3, runId);
        statement.executeUpdate();
      }
      return null;
    });
  }

  public void startNode(String runId, String nodeId) {
    long now = clock.millis();
    String sql =
        """
        INSERT INTO workflow_node (workflow_run_id, node_id, status, started_at)
        VALUES (?, ?, 'RUNNING', ?)
        ON CONFLICT (workflow_run_id, node_id) DO UPDATE SET status = 'RUNNING', started_at = excluded.started_at
        """;
    writer.writeVoid(() -> {
      try (Connection connection = database.connection();
          PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, runId);
        statement.setString(2, nodeId);
        statement.setLong(3, now);
        statement.executeUpdate();
      }
      return null;
    });
  }

  public void finishNode(String runId, String nodeId, String status, Map<String, Object> outputs) {
    long now = clock.millis();
    String sql =
        """
        UPDATE workflow_node SET status = ?, finished_at = ?, outputs = ?
        WHERE workflow_run_id = ? AND node_id = ?
        """;
    writer.writeVoid(() -> {
      try (Connection connection = database.connection();
          PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, status);
        statement.setLong(2, now);
        statement.setString(3, json(outputs));
        statement.setString(4, runId);
        statement.setString(5, nodeId);
        statement.executeUpdate();
      }
      return null;
    });
  }

  public Optional<WorkflowRun> findRun(String runId) {
    String sql = "SELECT id, name, status, definition, inputs FROM workflow_run WHERE id = ?";
    try (Connection connection = database.connection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, runId);
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next()) {
          return Optional.empty();
        }
        String storedId = result.getString("id");
        Map<String, String> statuses = new LinkedHashMap<>();
        nodes(storedId).forEach((nodeId, state) -> statuses.put(nodeId, state.status()));
        return Optional.of(
            new WorkflowRun(
                storedId,
                result.getString("name"),
                result.getString("status"),
                Map.copyOf(statuses),
                result.getString("definition"),
                params(result.getString("inputs"))));
      }
    } catch (SQLException error) {
      throw new DataAccessException("falha ao ler execucao de workflow", error);
    }
  }

  public List<WorkflowRun> recent(int limit) {
    String sql = "SELECT id, name, status FROM workflow_run ORDER BY started_at DESC LIMIT ?";
    try (Connection connection = database.connection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setInt(1, Math.max(1, limit));
      List<WorkflowRun> runs = new ArrayList<>();
      try (ResultSet result = statement.executeQuery()) {
        while (result.next()) {
          runs.add(
              new WorkflowRun(
                  result.getString("id"),
                  result.getString("name"),
                  result.getString("status"),
                  Map.of(),
                  null,
                  Map.of()));
        }
      }
      return List.copyOf(runs);
    } catch (SQLException error) {
      throw new DataAccessException("falha ao listar workflows", error);
    }
  }

  public Map<String, NodeState> nodes(String runId) {
    String sql = "SELECT node_id, status, outputs FROM workflow_node WHERE workflow_run_id = ?";
    try (Connection connection = database.connection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, runId);
      Map<String, NodeState> states = new LinkedHashMap<>();
      try (ResultSet result = statement.executeQuery()) {
        while (result.next()) {
          states.put(
              result.getString("node_id"),
              new NodeState(result.getString("status"), params(result.getString("outputs"))));
        }
      }
      return Map.copyOf(states);
    } catch (SQLException error) {
      throw new DataAccessException("falha ao ler estado dos passos", error);
    }
  }

  /** Estado persistido de um passo. */
  public record NodeState(String status, Map<String, Object> outputs) {}

  /** Execucao de workflow, com definicao e entradas para permitir retomada. */
  public record WorkflowRun(
      String id,
      String name,
      String status,
      Map<String, String> nodeStatuses,
      String definitionYaml,
      Map<String, Object> inputs) {}

  @SuppressWarnings("unchecked")
  private static Map<String, Object> params(String value) {
    if (value == null || value.isBlank()) {
      return Map.of();
    }
    try {
      return Map.copyOf(JSON.readValue(value, Map.class));
    } catch (JsonProcessingException error) {
      throw new DataAccessException("json invalido no banco", error);
    }
  }

  static String json(Map<String, Object> value) {
    try {
      return JSON.writeValueAsString(value == null ? Map.of() : value);
    } catch (JsonProcessingException error) {
      throw new IllegalArgumentException("nao foi possivel serializar saida", error);
    }
  }
}
