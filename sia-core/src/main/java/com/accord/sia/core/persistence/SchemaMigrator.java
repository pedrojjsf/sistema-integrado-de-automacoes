package com.accord.sia.core.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Aplica o schema versionado no SQLite, usando {@code PRAGMA user_version} como marcador.
 *
 * <p>Migracao roda antes de qualquer plugin: esquema velho com codigo novo e a origem classica de
 * "funcionava na minha maquina".
 */
public final class SchemaMigrator {

  private static final String SCHEMA_RESOURCE = "/schema/V1__init.sql";
  private static final int CURRENT_VERSION = 1;

  public void migrate(Database database) {
    try (Connection connection = database.connection()) {
      if (userVersion(connection) >= CURRENT_VERSION) {
        return;
      }
      for (String statement : script().split(";")) {
        String sql = statement.strip();
        if (sql.isEmpty()) {
          continue;
        }
        try (Statement executor = connection.createStatement()) {
          executor.execute(sql);
        }
      }
      try (Statement executor = connection.createStatement()) {
        executor.execute("PRAGMA user_version = " + CURRENT_VERSION);
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

  private static int userVersion(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("PRAGMA user_version")) {
      return result.next() ? result.getInt(1) : 0;
    }
  }

  private static String script() {
    try (InputStream resource = SchemaMigrator.class.getResourceAsStream(SCHEMA_RESOURCE)) {
      if (resource == null) {
        throw new DataAccessException("schema nao encontrado: " + SCHEMA_RESOURCE);
      }
      return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException error) {
      throw new DataAccessException("falha ao ler schema", error);
    }
  }
}
