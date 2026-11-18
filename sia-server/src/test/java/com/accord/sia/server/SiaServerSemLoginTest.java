package com.accord.sia.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.accord.sia.core.config.SiaConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code auth.mode: none} — sem login, porque a autenticação real acontece dentro das automações
 * (certificado digital, por exemplo). Só é aceitável em loopback.
 */
class SiaServerSemLoginTest {

  @TempDir static Path temp;

  private static SiaServer server;
  private static String base;
  private static final HttpClient http = HttpClient.newHttpClient();

  @BeforeAll
  static void start() throws Exception {
    SiaConfig config =
        new SiaConfig(
            new SiaConfig.Server("127.0.0.1", 0),
            new SiaConfig.Database(temp.resolve("sia.db").toString()),
            new SiaConfig.Plugins(temp.resolve("plugins").toString()),
            new SiaConfig.Auth("none", Map.of()),
            new SiaConfig.Scheduler("America/Sao_Paulo"),
            Map.of());

    server = SiaServer.create(config).start("127.0.0.1", 0);
    base = "http://127.0.0.1:" + server.port();
  }

  @AfterAll
  static void stop() {
    if (server != null) {
      server.close();
    }
  }

  @Test
  void healthRespondeSemToken() throws Exception {
    HttpResponse<String> response = get("/api/health");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"status\":\"ok\"").contains("\"auth\":\"none\"");
  }

  @Test
  void catalogoEEntregueSemToken() throws Exception {
    assertThat(get("/api/plugins").statusCode()).isEqualTo(200);
    assertThat(get("/api/automations").statusCode()).isEqualTo(200);
    assertThat(get("/api/runs").statusCode()).isEqualTo(200);
  }

  @Test
  void interfaceWebContinuaServida() throws Exception {
    assertThat(get("/index.html").statusCode()).isEqualTo(200);
  }

  @Test
  void recusaModoNoneForaDeLoopback() {
    SiaConfig exposto =
        new SiaConfig(
            new SiaConfig.Server("0.0.0.0", 0),
            new SiaConfig.Database(temp.resolve("outro.db").toString()),
            new SiaConfig.Plugins(temp.resolve("plugins").toString()),
            new SiaConfig.Auth("none", Map.of()),
            Map.of());

    assertThatThrownBy(() -> SiaServer.create(exposto))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("loopback");
  }

  private static HttpResponse<String> get(String path) throws Exception {
    return http.send(
        HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
