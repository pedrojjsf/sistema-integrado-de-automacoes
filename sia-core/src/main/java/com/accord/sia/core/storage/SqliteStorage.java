package com.accord.sia.core.storage;

import com.accord.sia.api.Storage;
import com.accord.sia.core.persistence.DataAccessException;
import com.accord.sia.core.persistence.Database;
import com.accord.sia.core.persistence.SingleWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Armazenamento chave-valor por plugin, na tabela {@code kv_store}.
 *
 * <p>Toda consulta filtra por {@code plugin_id}: um plugin nao alcanca a chave de outro nem por
 * engano de codigo. A chave e validada para nao carregar separador de caminho, porque ela vira
 * tambem nome de arquivo em backup/log.
 */
public final class SqliteStorage implements Storage {

  private static final int MAX_KEY_LENGTH = 200;

  private final Database database;
  private final SingleWriter writer;
  private final String pluginId;
  private final Clock clock;

  public SqliteStorage(Database database, SingleWriter writer, String pluginId, Clock clock) {
    this.database = database;
    this.writer = writer;
    this.pluginId = pluginId;
    this.clock = clock;
  }

  @Override
  public Optional<String> get(String key) {
    validateKey(key);
    String sql = "SELECT value FROM kv_store WHERE plugin_id = ? AND key = ?";
    try (Connection connection = database.connection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, pluginId);
      statement.setString(2, key);
      try (ResultSet result = statement.executeQuery()) {
        return result.next() ? Optional.of(result.getString(1)) : Optional.empty();
      }
    } catch (SQLException error) {
      throw new DataAccessException("falha ao ler chave " + key, error);
    }
  }

  @Override
  public void put(String key, String value) {
    validateKey(key);
    if (value == null) {
      throw new IllegalArgumentException("valor nulo para a chave " + key);
    }
    long now = clock.millis();
    String sql =
        """
        INSERT INTO kv_store (plugin_id, key, value, updated_at) VALUES (?, ?, ?, ?)
        ON CONFLICT (plugin_id, key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at
        """;
    writer.writeVoid(() -> {
      try (Connection connection = database.connection();
          PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, pluginId);
        statement.setString(2, key);
        statement.setString(3, value);
        statement.setLong(4, now);
        statement.executeUpdate();
      }
      return null;
    });
  }

  @Override
  public void delete(String key) {
    validateKey(key);
    String sql = "DELETE FROM kv_store WHERE plugin_id = ? AND key = ?";
    writer.writeVoid(() -> {
      try (Connection connection = database.connection();
          PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, pluginId);
        statement.setString(2, key);
        statement.executeUpdate();
      }
      return null;
    });
  }

  @Override
  public List<String> keys(String prefix) {
    String safePrefix = prefix == null ? "" : prefix;
    String sql =
        "SELECT key FROM kv_store WHERE plugin_id = ? AND key LIKE ? ESCAPE '\\' ORDER BY key";
    try (Connection connection = database.connection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, pluginId);
      statement.setString(2, escapeLike(safePrefix) + "%");
      List<String> keys = new ArrayList<>();
      try (ResultSet result = statement.executeQuery()) {
        while (result.next()) {
          keys.add(result.getString(1));
        }
      }
      return List.copyOf(keys);
    } catch (SQLException error) {
      throw new DataAccessException("falha ao listar chaves", error);
    }
  }

  private static String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  static void validateKey(String key) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("chave vazia");
    }
    if (key.length() > MAX_KEY_LENGTH) {
      throw new IllegalArgumentException("chave maior que " + MAX_KEY_LENGTH + " caracteres");
    }
    if (key.indexOf('/') >= 0 || key.indexOf('\\') >= 0 || key.contains("..")) {
      throw new IllegalArgumentException("chave nao pode conter caminho: " + key);
    }
    if (key.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("chave com caractere de controle");
    }
  }
}
