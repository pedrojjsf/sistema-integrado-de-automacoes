package com.accord.sia.core.process;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Processo de plugin falado por JSON Lines ({@code stdio-jsonl-v1}).
 *
 * <p>Uma linha, um objeto JSON. O Core envia {@code describe}, {@code execute}, {@code cancel} e
 * {@code shutdown}; o plugin responde {@code describe}, {@code result}, {@code log}, {@code
 * progress} e {@code error}. Linhas que nao sao JSON sao registradas e descartadas — um {@code
 * print} perdido no plugin nao derruba o host.
 */
public final class ProcessPluginHost implements AutoCloseable {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Set<String> STREAM_TYPES = Set.of("log", "progress", "error");

  private final Process process;
  private final BufferedWriter stdin;
  private final BlockingQueue<JsonNode> inbox = new LinkedBlockingQueue<>();
  private final Consumer<JsonNode> events;
  private final AtomicBoolean closed = new AtomicBoolean();
  private final Thread reader;

  private ProcessPluginHost(Process process, Consumer<JsonNode> events, String name) {
    this.process = process;
    this.events = events;
    this.stdin =
        new BufferedWriter(
            new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
    this.reader = Thread.ofPlatform().daemon(true).name("plugin-stdout-" + name).start(this::read);
  }

  public static ProcessPluginHost start(
      List<String> command, Path workingDirectory, Consumer<JsonNode> events) throws IOException {
    if (command == null || command.isEmpty()) {
      throw new IOException("comando do plugin vazio");
    }
    ProcessBuilder builder = new ProcessBuilder(command);
    if (workingDirectory != null) {
      builder.directory(workingDirectory.toFile());
    }
    builder.redirectErrorStream(false);
    Process process = builder.start();
    return new ProcessPluginHost(process, events, command.get(0));
  }

  public void send(ObjectNode message) throws IOException {
    synchronized (stdin) {
      stdin.write(JSON.writeValueAsString(message));
      stdin.newLine();
      stdin.flush();
    }
  }

  /** Espera a proxima mensagem de resposta; {@code log}/{@code progress} vao para o listener. */
  public JsonNode await(Duration timeout) {
    try {
      JsonNode node = inbox.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (node == null) {
        throw new IllegalStateException("plugin nao respondeu em " + timeout.toSeconds() + "s");
      }
      if ("__eof".equals(node.path("type").asText())) {
        throw new IllegalStateException(
            "plugin terminou a execucao (exit " + process.exitValue() + ")");
      }
      return node;
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("espera pelo plugin interrompida", interrupted);
    }
  }

  public boolean isAlive() {
    return process.isAlive();
  }

  private void read() {
    try (BufferedReader stdout =
        new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = stdout.readLine()) != null) {
        JsonNode node = parse(line);
        if (node == null) {
          continue;
        }
        if (STREAM_TYPES.contains(node.path("type").asText())) {
          if (events != null) {
            events.accept(node);
          }
        } else {
          inbox.add(node);
        }
      }
    } catch (IOException ignored) {
      // processo morreu: o EOF abaixo sinaliza
    } finally {
      ObjectNode end = JSON.createObjectNode();
      end.put("type", "__eof");
      inbox.add(end);
    }
  }

  private static JsonNode parse(String line) {
    String trimmed = line.trim();
    if (trimmed.isEmpty() || !trimmed.startsWith("{")) {
      return null;
    }
    try {
      return JSON.readTree(trimmed);
    } catch (IOException ignored) {
      return null;
    }
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    try {
      ObjectNode shutdown = JSON.createObjectNode();
      shutdown.put("type", "shutdown");
      send(shutdown);
    } catch (IOException ignored) {
      // processo ja pode ter saido
    }
    process.destroy();
    try {
      if (!process.waitFor(5, TimeUnit.SECONDS)) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
    }
    reader.interrupt();
  }
}
