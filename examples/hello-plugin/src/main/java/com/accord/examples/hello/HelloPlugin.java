package com.accord.examples.hello;

import com.accord.sia.api.Automation;
import com.accord.sia.api.AutomationPlugin;
import com.accord.sia.api.PluginContext;
import com.accord.sia.api.PluginMetadata;
import java.util.List;

/** Plugin minimo: serve de referencia de estrutura, nao de regra de negocio. */
public final class HelloPlugin implements AutomationPlugin {

  @Override
  public PluginMetadata metadata() {
    return new PluginMetadata("hello-plugin", "Hello Plugin", "1.0.0", "1.0", "Accord");
  }

  @Override
  public List<Automation> automations() {
    return List.of(new SayHello());
  }

  @Override
  public void initialize(PluginContext context) {
    context.log().info("hello-plugin pronto");
  }

  @Override
  public void shutdown() {
    // nada a liberar: sem threads, sem conexao
  }
}
