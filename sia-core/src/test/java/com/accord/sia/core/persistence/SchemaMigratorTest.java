package com.accord.sia.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaMigratorTest {

  @TempDir Path temp;

  @Test
  void criaAsTabelasDoSchema() throws Exception {
    Database database = Database.open(temp.resolve("sia.db"));

    new SchemaMigrator().migrate(database);

    assertThat(tables(database))
        .contains("plugin_state", "run", "run_log", "schedule", "kv_store", "workflow_node");
  }

  @Test
  void migracaoEhIdempotente() throws Exception {
    Database database = Database.open(temp.resolve("sia.db"));
    SchemaMigrator migrator = new SchemaMigrator();

    migrator.migrate(database);
    migrator.migrate(database);

    assertThat(migrator.userVersion(database)).isEqualTo(1);
    assertThat(tables(database)).contains("kv_store");
  }

  private static java.util.List<String> tables(Database database) throws SQLException {
    java.util.List<String> names = new java.util.ArrayList<>();
    try (Connection connection = database.connection();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'")) {
      while (result.next()) {
        names.add(result.getString(1));
      }
    }
    return names;
  }

  @Test
  void bancoCriadoEmPastaInexistente() throws IOException {
    Database database = Database.open(temp.resolve("sub").resolve("dir").resolve("sia.db"));

    new SchemaMigrator().migrate(database);

    assertThat(database.url()).contains("sia.db");
  }
}
