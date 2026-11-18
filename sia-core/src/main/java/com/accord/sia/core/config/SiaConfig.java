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
    Scheduler scheduler,
    Map<String, ProcessSpec> processes) {

  /** Compatibilidade: sem a secao {@code scheduler}, o fuso padrao e o de Brasilia. */
  public SiaConfig(
      Server server,
      Database database,
      Plugins plugins,
      Auth auth,
      Map<String, ProcessSpec> processes) {
    this(server, database, plugins, auth, null, processes);
  }

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

  /**
   * Fuso do SIA. Usado para avaliar cron e para devolver horarios na API, para que a agenda das
   * 03:00 signifique 03:00 de Brasilia em qualquer maquina — servidor em UTC inclusive.
   *
   * @param zone identificador IANA; padrao {@code America/Sao_Paulo}
   */
  public record Scheduler(String zone) {

    public Scheduler {
      zone = zone == null || zone.isBlank() ? "America/Sao_Paulo" : zone;
      java.time.ZoneId.of(zone); // valida cedo, em vez de falhar no meio de um disparo
    }

    public java.time.ZoneId zoneId() {
      return java.time.ZoneId.of(zone);
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
    scheduler = scheduler == null ? new Scheduler(null) : scheduler;
    processes = processes == null ? Map.of() : Map.copyOf(processes);
  }

  public static SiaConfig defaults() {
    return new SiaConfig(null, null, null, null, null, Map.of());
  }
}
