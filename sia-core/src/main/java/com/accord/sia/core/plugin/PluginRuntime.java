package com.accord.sia.core.plugin;

import com.accord.sia.api.AutomationPlugin;
import com.accord.sia.api.PluginContext;

/**
 * Visao do Core sobre um plugin pronto para executar.
 *
 * <p>Existe para o engine nao depender do loader: em teste basta uma implementacao simples, e o
 * modo {@code process} (T4c) pode fornecer outra sem mexer no engine.
 */
public interface PluginRuntime {

  String id();

  PluginState state();

  AutomationPlugin instance();

  PluginContext context();
}
