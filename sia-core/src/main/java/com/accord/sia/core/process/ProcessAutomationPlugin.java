package com.accord.sia.core.process;

import com.accord.sia.api.Automation;
import com.accord.sia.api.AutomationContext;
import com.accord.sia.api.AutomationPlugin;
import com.accord.sia.api.PluginContext;
import com.accord.sia.api.PluginLoadException;
import com.accord.sia.api.PluginMetadata;
import com.accord.sia.core.manifest.PluginManifest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Adaptador de um plugin {@code kind: process} para o contrato {@link AutomationPlugin}.
 *
 * <p>O Core continua falando "plugin" e "automacao"; por baixo, cada automacao vira uma mensagem
 * JSON para o processo filho. Engine, scheduler, workflow e historico nao precisam saber que existe
 * um processo do outro lado.
 */
public final class ProcessAutomationPlugin implements AutomationPlugin {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Duration HANDSHAKE = Duration.ofSeconds(15);
  private static final Duration EXECUTE_TIMEOUT = Duration.ofMinutes(30);

  private final PluginMetadata metadata;
  private final List<Automation> automations;
  private final ProcessPluginHost host;

  private ProcessAutomationPlugin(
      PluginMetadata metadata, List<Automation> automations, ProcessPluginHost host) {
    this.metadata = metadata;
    this.automations = List.copyOf(automations);
    this.host = host;
  }

  public static ProcessAutomationPlugin start(PluginManifest manifest, Path directory)
      throws PluginLoadException {
    AtomicReference<AutomationContext> active = new AtomicReference<>();
    List<String> command = resolve(manifest.command(), directory);
    ProcessPluginHost host;
    try {
      host = ProcessPluginHost.start(command, directory, node -> route(node, active));
    } catch (IOException error) {
      throw new PluginLoadException(manifest.id(), "nao foi possivel iniciar o plugin", error);
    }
    try {
      ObjectNode describe = JSON.createObjectNode();
      describe.put("type", "describe");
      describe.put("protocol", "stdio-jsonl-v1");
      host.send(describe);
      JsonNode response = host.await(HANDSHAKE);
      if (!"describe".equals(response.path("type").asText())) {
        throw new PluginLoadException(
            manifest.id(), "plugin respondeu '" + response.path("type").asText() + "' ao describe");
      }
      JsonNode meta = response.path("metadata");
      PluginMetadata metadata =
          new PluginMetadata(
              meta.path("id").asText(manifest.id()),
              meta.path("name").asText(manifest.name()),
              meta.path("version").asText(manifest.version()),
              meta.path("apiVersion").asText("1.0"),
              meta.path("author").asText(manifest.author()));

      List<Automation> automations = new ArrayList<>();
      for (JsonNode node : response.path("automations")) {
        automations.add(new ProcessAutomation(host, active, node, EXECUTE_TIMEOUT));
      }
      return new ProcessAutomationPlugin(metadata, automations, host);
    } catch (IOException | IllegalStateException error) {
      host.close();
      throw new PluginLoadException(
          manifest.id(), "handshake falhou: " + error.getMessage(), error);
    }
  }

  /** {@code log}/{@code progress} do plugin entram no log e no progresso da execucao atual. */
  private static void route(JsonNode node, AtomicReference<AutomationContext> active) {
    AutomationContext context = active.get();
    if (context == null) {
      return;
    }
    switch (node.path("type").asText()) {
      case "log" -> context.log().info(node.path("message").asText(""));
      case "progress" ->
          context
              .progress()
              .progress(node.path("percent").asInt(0), node.path("message").asText(""));
      case "error" -> context.log().warn(node.path("message").asText("erro no plugin"));
      default -> {
        // tipo desconhecido: ignorado para nao quebrar a execucao
      }
    }
  }

  private static List<String> resolve(List<String> command, Path directory) {
    if (command.isEmpty()) {
      return command;
    }
    List<String> resolved = new ArrayList<>(command);
    Path first = Path.of(command.get(0));
    Path candidate = directory.resolve(first);
    if (!first.isAbsolute() && Files.isRegularFile(candidate)) {
      resolved.set(0, candidate.toAbsolutePath().toString());
    }
    return resolved;
  }

  @Override
  public PluginMetadata metadata() {
    return metadata;
  }

  @Override
  public List<Automation> automations() {
    return automations;
  }

  @Override
  public void initialize(PluginContext context) {
    context.log().info("plugin de processo conectado");
  }

  @Override
  public void shutdown() {
    host.close();
  }
}
