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
  void bloqueiaPostCrossOrigin() throws Exception {
    HttpResponse<String> response =
        post("/api/runs", Map.of("Origin", "http://evil.example"), BODY);

    assertThat(response.statusCode()).isEqualTo(403);
  }

  @Test
  void bloqueiaHostQueNaoSejaLoopback() throws Exception {
    // HttpClient não deixa forçar o header Host, então mandamos o pedido na mão por socket
    String statusLine = rawPost("evil.example", BODY);

    assertThat(statusLine).startsWith("HTTP/1.1 403");
  }

  private String rawPost(String host, String body) throws Exception {
    try (java.net.Socket socket = new java.net.Socket("127.0.0.1", server.port())) {
      socket.setSoTimeout(5000);
      byte[] payload = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      String request =
          "POST /api/runs HTTP/1.1\r\n"
              + "Host: "
              + host
              + "\r\n"
              + "Content-Type: application/json\r\n"
              + "Content-Length: "
              + payload.length
              + "\r\n"
              + "Connection: close\r\n\r\n";
      java.io.OutputStream out = socket.getOutputStream();
      out.write(request.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
      out.write(payload);
      out.flush();
      try (java.io.BufferedReader reader =
          new java.io.BufferedReader(
              new java.io.InputStreamReader(
                  socket.getInputStream(), java.nio.charset.StandardCharsets.US_ASCII))) {
        return reader.readLine();
      }
    }
  }

  @Test
  void aceitaPostSemOriginEHospedeiroLoopback() throws Exception {
    HttpResponse<String> response =
        post("/api/runs", Map.of("Origin", "http://127.0.0.1:8080"), BODY);

    // não é 403: o corpo inválido chega ao handler e vira 400
    assertThat(response.statusCode()).isEqualTo(400);
  }

  private static final String BODY = "{\"automation\":\"nao.existe\",\"params\":{}}";

  private static HttpResponse<String> post(String path, Map<String, String> headers, String body)
      throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(base + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
    headers.forEach(builder::header);
    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
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
