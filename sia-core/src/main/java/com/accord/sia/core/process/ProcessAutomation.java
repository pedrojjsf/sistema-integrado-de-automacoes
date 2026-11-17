package com.accord.sia.core.process;

import com.accord.sia.api.Automation;
import com.accord.sia.api.AutomationContext;
import com.accord.sia.api.AutomationDescriptor;
import com.accord.sia.api.AutomationResult;
import com.accord.sia.api.ParamSpec;
import com.accord.sia.api.ParamType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Uma automacao publicada por um plugin de processo: encaminha {@code execute} e traduz a resposta.
 */
final class ProcessAutomation implements Automation {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final ProcessPluginHost host;
  private final AtomicReference<AutomationContext> active;
  private final AutomationDescriptor descriptor;
  private final Duration timeout;

  ProcessAutomation(
      ProcessPluginHost host,
      AtomicReference<AutomationContext> active,
      JsonNode describeNode,
      Duration timeout) {
    this.host = host;
    this.active = active;
    this.descriptor = descriptor(describeNode);
    this.timeout = timeout;
  }

  @Override
  public AutomationDescriptor describe() {
    return descriptor;
  }

  @Override
  public AutomationResult execute(AutomationContext context) {
    synchronized (host) {
      active.set(context);
      try {
        ObjectNode request = JSON.createObjectNode();
        request.put("type", "execute");
        request.put("runId", context.runId().value());
        request.put("automation", descriptor.id());
        ObjectNode params = request.putObject("params");
        context.params().asMap().forEach((key, value) -> params.put(key, String.valueOf(value)));

        context.progress().step("enviando para o plugin");
        host.send(request);
        while (true) {
          JsonNode response = host.await(timeout);
          String type = response.path("type").asText();
          if ("result".equals(type)) {
            return toResult(response);
          }
          if ("error".equals(type)) {
            return AutomationResult.failure(
                "PLUGIN_ERROR", response.path("message").asText("erro no plugin"));
          }
        }
      } catch (java.io.IOException error) {
        return AutomationResult.failure("PLUGIN_IO", "falha ao falar com o plugin");
      } catch (IllegalStateException error) {
        return AutomationResult.failure("PLUGIN_TIMEOUT", error.getMessage());
      } finally {
        active.compareAndSet(context, null);
      }
    }
  }

  private AutomationResult toResult(JsonNode response) {
    String status = response.path("status").asText("FAILURE");
    String message =
        response.path("message").isMissingNode() ? null : response.path("message").asText();
    Map<String, Object> outputs = new LinkedHashMap<>();
    JsonNode outputsNode = response.path("outputs");
    if (outputsNode.isObject()) {
      outputsNode
          .fields()
          .forEachRemaining(entry -> outputs.put(entry.getKey(), entry.getValue().asText()));
    }
    return switch (status) {
      case "SUCCESS" -> AutomationResult.success(outputs, message);
      case "SKIPPED" -> AutomationResult.skipped(message == null ? "pulado" : message);
      default ->
          AutomationResult.failure(response.path("errorCode").asText("PLUGIN_FAILURE"), message);
    };
  }

  static AutomationDescriptor descriptor(JsonNode node) {
    List<ParamSpec> params = new ArrayList<>();
    JsonNode paramsNode = node.path("params");
    if (paramsNode.isArray()) {
      for (JsonNode param : paramsNode) {
        params.add(
            new ParamSpec(
                param.path("name").asText(),
                ParamType.valueOf(param.path("type").asText("STRING")),
                param.path("required").asBoolean(false),
                param.path("sensitive").asBoolean(false),
                param.path("description").asText(null),
                param.path("defaultValue").isMissingNode()
                    ? null
                    : param.path("defaultValue").asText(null)));
      }
    }
    return new AutomationDescriptor(
        node.path("id").asText(),
        node.path("name").asText(),
        node.path("description").asText(""),
        params,
        node.path("persistOutputs").asBoolean(false));
  }
}
