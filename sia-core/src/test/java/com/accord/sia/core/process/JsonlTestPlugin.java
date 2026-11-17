package com.accord.sia.core.process;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Plugin de processo de teste: fala {@code stdio-jsonl-v1} sem depender de nenhuma biblioteca.
 *
 * <p>Serve para provar que o host aceita plugin escrito em qualquer linguagem — aqui, um main Java
 * solto, sem classpath do SIA.
 */
public final class JsonlTestPlugin {

  private JsonlTestPlugin() {}

  public static void main(String[] args) throws Exception {
    PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
    BufferedReader in =
        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    String line;
    while ((line = in.readLine()) != null) {
      if (line.contains("\"describe\"")) {
        out.println(
            "{\"type\":\"describe\","
                + "\"metadata\":{\"id\":\"process-plugin\",\"name\":\"Process\","
                + "\"version\":\"1.0.0\",\"apiVersion\":\"1.0\"},"
                + "\"automations\":[{\"id\":\"process.eco\",\"name\":\"Eco\","
                + "\"description\":\"devolve o texto\","
                + "\"params\":[{\"name\":\"texto\",\"type\":\"STRING\",\"required\":true,"
                + "\"sensitive\":false}]}]}");
      } else if (line.contains("\"execute\"")) {
        String runId = between(line, "\"runId\":\"");
        String texto = between(line, "\"texto\":\"");
        out.println(
            "{\"type\":\"log\",\"runId\":\""
                + runId
                + "\",\"level\":\"INFO\","
                + "\"message\":\"executando eco\"}");
        out.println(
            "{\"type\":\"result\",\"runId\":\""
                + runId
                + "\",\"status\":\"SUCCESS\",\"message\":\"ok\","
                + "\"outputs\":{\"eco\":\""
                + texto
                + "\"}}");
      } else if (line.contains("\"shutdown\"")) {
        return;
      }
    }
  }

  private static String between(String line, String marker) {
    int start = line.indexOf(marker);
    if (start < 0) {
      return "";
    }
    int from = start + marker.length();
    int end = line.indexOf('"', from);
    return end < 0 ? "" : line.substring(from, end);
  }
}
