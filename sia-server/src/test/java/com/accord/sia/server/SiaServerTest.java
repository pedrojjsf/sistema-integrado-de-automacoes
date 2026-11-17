package com.accord.sia.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.accord.sia.core.config.SiaConfig;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SiaServerTest {

  @TempDir static Path temp;

  private static SiaServer server;
  private static HttpClient http;
  private static String base;

  @BeforeAll
  static void startDaemon() throws IOException {
    SiaConfig config =
        new SiaConfig(
            new SiaConfig.Server("127.0.0.1", 0),
            new SiaConfig.Database(temp.resolve("sia.db").toString()),
            new SiaConfig.Plugins(temp.resolve("plugins").toString()),
            new SiaConfig.Auth(
                "dev",
                Map.of(
                    "admin-token",
                    new SiaConfig.Auth.Token("sub-admin", "Admin", List.of("admin")),
                    "viewer-token",
                    new SiaConfig.Auth.Token("sub-viewer", "Viewer", List.of("viewer")))),
            Map.of());
    server = SiaServer.create(config).start("127.0.0.1", 0);
    base = "http://127.0.0.1:" + server.port();
    http = HttpClient.newHttpClient();
  }

  @AfterAll
  static void stopDaemon() {
    server.close();
  }

  @Test
  void semTokenResponde401() throws Exception {
    assertThat(get("/api/health", null).statusCode()).isEqualTo(401);
  }

  @Test
  void tokenInvalidoResponde401() throws Exception {
    assertThat(get("/api/health", "token-errado").statusCode()).isEqualTo(401);
  }

  @Test
  void healthInformaModoDeAuth() throws Exception {
    HttpResponse<String> response = get("/api/health", "admin-token");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"status\":\"ok\"").contains("\"auth\":\"dev\"");
  }

  @Test
  void serveAConsultaWebNaRaiz() throws Exception {
    HttpResponse<String> redirect =
        http.send(
            java.net.http.HttpRequest.newBuilder(URI.create(base + "/")).GET().build(),
            HttpResponse.BodyHandlers.ofString());

    assertThat(redirect.statusCode()).isEqualTo(302);
    assertThat(redirect.headers().firstValue("Location"))
        .hasValueSatisfying(location -> assertThat(location).endsWith("/index.html"));

    HttpResponse<String> page = get("/index.html", "admin-token");
    assertThat(page.statusCode()).isEqualTo(200);
    assertThat(page.body()).contains("Sistema Integrado de Automações").contains("/app.js");
  }

  @Test
  void entregaFolhasDeEstiloValidas() throws Exception {
    HttpResponse<String> css = get("/styles.css", "admin-token");

    assertThat(css.statusCode()).isEqualTo(200);
    assertThat(css.body()).startsWith(":root");
    // aspas soltas ja quebraram o CSS inteiro uma vez: a regra de [hidden] ficou dentro
    // de uma string aberta e o login nunca sumia.
    assertThat(css.body()).doesNotContain("'");
    assertThat(css.body()).contains("[hidden] { display: none !important; }");
    long open = css.body().chars().filter(c -> c == '{').count();
    long close = css.body().chars().filter(c -> c == '}').count();
    assertThat(open).isEqualTo(close);
  }

  @Test
  void entregaScriptDaInterfaceIntegro() throws Exception {
    HttpResponse<String> js = get("/app.js", "admin-token");

    assertThat(js.statusCode()).isEqualTo(200);
    assertThat(js.body()).contains("function startApp").contains("function showLogin");
    // aspas soltas no inicio de linha ja quebraram o script inteiro (tela em branco)
    assertThat(js.body().lines().noneMatch(line -> line.stripLeading().startsWith("'")))
        .as("nenhuma linha pode comecar com apostrofo solto")
        .isTrue();
    long open = js.body().chars().filter(c -> c == '{').count();
    long close = js.body().chars().filter(c -> c == '}').count();
    assertThat(open).isEqualTo(close);
  }

  @Test
  void viewerLeCatalogoMasNaoExecuta() throws Exception {
    assertThat(get("/api/plugins", "viewer-token").statusCode()).isEqualTo(200);
    assertThat(get("/api/automations", "viewer-token").statusCode()).isEqualTo(200);

    HttpResponse<String> post =
        post("/api/runs", "viewer-token", "{\"automation\":\"x.y\",\"params\":{}}");
    assertThat(post.statusCode()).isEqualTo(403);
  }

  @Test
  void automacaoDesconhecidaResponde400() throws Exception {
    HttpResponse<String> response =
        post("/api/runs", "admin-token", "{\"automation\":\"nao.existe\",\"params\":{}}");

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).contains("automacao desconhecida");
  }

  @Test
  void uninstallDePluginDesconhecidoResponde400() throws Exception {
    HttpResponse<String> response = post("/api/plugins/nao-existe/uninstall", "admin-token", "{}");

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).contains("plugin desconhecido");
  }

  @Test
  void execucaoDesconhecidaResponde400() throws Exception {
    assertThat(get("/api/runs/nao-existe", "admin-token").statusCode()).isEqualTo(400);
  }

  @Test
  void listaDeExecucoesComecaVazia() throws Exception {
    HttpResponse<String> response = get("/api/runs?limit=5", "admin-token");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("[]");
  }

  private static HttpResponse<String> get(String path, String token) throws Exception {
    return http.send(request(path, token).GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  private static HttpResponse<String> post(String path, String token, String body)
      throws Exception {
    return http.send(
        request(path, token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static HttpRequest.Builder request(String path, String token) {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path));
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    return builder;
  }
}
