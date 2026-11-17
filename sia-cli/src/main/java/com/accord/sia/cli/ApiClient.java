package com.accord.sia.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Cliente HTTP do daemon. O CLI nao toca banco nem disco do Core. */
final class ApiClient {

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final ObjectMapper json = new ObjectMapper();
  private final String baseUrl;
  private final String token;

  ApiClient(String baseUrl, String token) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.token = token == null ? "" : token;
  }

  JsonNode get(String path) {
    return send(request(path).GET().build());
  }

  JsonNode post(String path, Object body) {
    try {
      String payload = json.writeValueAsString(body);
      return send(
          request(path)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(payload))
              .build());
    } catch (IOException error) {
      throw new CliException("falha ao montar requisicao: " + error.getMessage());
    }
  }

  private HttpRequest.Builder request(String path) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(30));
    if (!token.isBlank()) {
      builder.header("Authorization", "Bearer " + token);
    }
    return builder;
  }

  private JsonNode send(HttpRequest request) {
    try {
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
      JsonNode body =
          response.body() == null || response.body().isBlank()
              ? json.createObjectNode()
              : json.readTree(response.body());
      if (response.statusCode() >= 400) {
        String message = body.path("error").asText("HTTP " + response.statusCode());
        throw new CliException(
            response.statusCode() == 401 ? "nao autenticado: " + message : message);
      }
      return body;
    } catch (IOException error) {
      throw new CliException("daemon inacessivel em " + baseUrl + " (" + describe(error) + ")");
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new CliException("requisicao interrompida");
    }
  }

  private static String describe(IOException error) {
    String message = error.getMessage();
    return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
  }

  /** Falha de uso ou de rede, com mensagem pronta para o usuario. */
  static final class CliException extends RuntimeException {
    CliException(String message) {
      super(message);
    }
  }
}
