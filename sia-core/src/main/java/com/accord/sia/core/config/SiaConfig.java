package com.accord.sia.core.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Configuracao do daemon ({@code config.yml}).
 *
 * <p>Um arquivo so, com defaults seguros: bind local, banco em {@code data/}, plugins em {@code
 * plugins/} e autenticacao em modo {@code dev} (que o daemon recusa em producao).
 */
public record SiaConfig(
    Server server,
    Database database,
    Plugins plugins,
    Auth auth,
    Map<String, ProcessSpec> processes) {

  public record Server(String host, int port) {

    public Server {
      host = host == null || host.isBlank() ? "127.0.0.1" : host;
      port = port <= 0 ? 8080 : port;
    }
  }

  public record Database(String path) {

    public Database {
      path = path == null || path.isBlank() ? "data/sia.db" : path;
    }
  }

  public record Plugins(String directory) {

    public Plugins {
      directory = directory == null || directory.isBlank() ? "plugins" : directory;
    }
  }

  public record Auth(String mode, Map<String, Token> tokens) {

    public Auth {
      mode = mode == null || mode.isBlank() ? "dev" : mode;
      tokens = tokens == null ? Map.of() : Map.copyOf(tokens);
    }

    public record Token(String subject, String name, List<String> roles) {

      public Token {
        roles = roles == null ? List.of() : List.copyOf(roles);
      }
    }
  }

  /** Comando base de um executavel permitido, resolvido por nome logico. */
  public record ProcessSpec(
      List<String> command, @JsonProperty("timeout-minutes") Integer timeoutMinutes) {

    public ProcessSpec {
      command = command == null ? List.of() : List.copyOf(command);
      timeoutMinutes = timeoutMinutes == null || timeoutMinutes <= 0 ? 10 : timeoutMinutes;
    }
  }

  public SiaConfig {
    server = server == null ? new Server(null, 0) : server;
    database = database == null ? new Database(null) : database;
    plugins = plugins == null ? new Plugins(null) : plugins;
    auth = auth == null ? new Auth(null, Map.of()) : auth;
    processes = processes == null ? Map.of() : Map.copyOf(processes);
  }

  public static SiaConfig defaults() {
    return new SiaConfig(null, null, null, null, Map.of());
  }
}
