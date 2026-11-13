package com.accord.sia.core.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Fabrica de conexoes SQLite com os pragmas que o projeto depende.
 *
 * <p>{@code WAL} permite leitura concorrente com escrita; {@code busy_timeout} evita falha imediata
 * quando outra conexao esta escrevendo; {@code foreign_keys} fica ligado porque o padrao do SQLite
 * e desligado.
 */
public final class Database {

  private final String url;

  private Database(String url) {
    this.url = url;
  }

  public static Database open(Path file) throws IOException {
    Path absolute = file.toAbsolutePath();
    if (absolute.getParent() != null) {
      Files.createDirectories(absolute.getParent());
    }
    return new Database("jdbc:sqlite:" + absolute);
  }

  public Connection connection() throws SQLException {
    Connection connection = DriverManager.getConnection(url);
    try (Statement statement = connection.createStatement()) {
      statement.execute("PRAGMA foreign_keys = ON");
      statement.execute("PRAGMA busy_timeout = 5000");
      statement.execute("PRAGMA journal_mode = WAL");
    } catch (SQLException error) {
      connection.close();
      throw error;
    }
    return connection;
  }

  public String url() {
    return url;
  }
}
