package com.accord.sia.core.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Aplica o schema versionado no SQLite, usando {@code PRAGMA user_version} como marcador.
 *
 * <p>Cada script tem versao propria e roda uma unica vez. Migracao roda antes de qualquer plugin:
 * esquema velho com codigo novo e a origem classica de "funcionava na minha maquina".
 */
public final class SchemaMigrator {

  private static final List<String> SCRIPTS =
      List.of("/schema/V1__init.sql", "/schema/V2__run_details.sql", "/schema/V3__idempotency.sql");

  public void migrate(Database database) {
    try (Connection connection = database.connection()) {
      int current = userVersion(connection);
      for (int version = current + 1; version <= SCRIPTS.size(); version++) {
        apply(connection, SCRIPTS.get(version - 1));
        setUserVersion(connection, version);
      }
    } catch (SQLException error) {
      throw new DataAccessException("falha ao migrar schema", error);
    }
  }

  public int userVersion(Database database) {
    try (Connection connection = database.connection()) {
      return userVersion(connection);
    } catch (SQLException error) {
      throw new DataAccessException("falha ao ler versao do schema", error);
    }
  }

  private static void apply(Connection connection, String resource) throws SQLException {
    for (String statement : script(resource).split(";")) {
      String sql = statement.strip();
      if (sql.isEmpty()) {
        continue;
      }
      try (Statement executor = connection.createStatement()) {
        executor.execute(sql);
      }
    }
  }

  private static void setUserVersion(Connection connection, int version) throws SQLException {
    try (Statement executor = connection.createStatement()) {
      executor.execute("PRAGMA user_version = " + version);
    }
  }

  private static int userVersion(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("PRAGMA user_version")) {
      return result.next() ? result.getInt(1) : 0;
    }
  }

  private static String script(String resource) {
    try (InputStream stream = SchemaMigrator.class.getResourceAsStream(resource)) {
      if (stream == null) {
        throw new DataAccessException("schema nao encontrado: " + resource);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException error) {
      throw new DataAccessException("falha ao ler schema " + resource, error);
    }
  }
}
