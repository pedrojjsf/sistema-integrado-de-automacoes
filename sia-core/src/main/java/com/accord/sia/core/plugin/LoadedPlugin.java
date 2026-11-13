package com.accord.sia.core.plugin;

import com.accord.sia.api.AutomationPlugin;
import com.accord.sia.api.PluginContext;
import com.accord.sia.api.PluginLoadException;
import com.accord.sia.core.manifest.PluginManifest;
import java.io.IOException;

/**
 * Plugin carregado: manifesto, instancia e classloader, com o estado do ciclo de vida.
 *
 * <p>Metodos de transicao sao sincronizados: o daemon pode pedir {@code reload} enquanto um run
 * termina, e o estado nao pode andar para tras.
 */
public final class LoadedPlugin implements PluginRuntime, AutoCloseable {

  private final PluginManifest manifest;
  private final AutomationPlugin instance;
  private final PluginClassLoader classLoader;
  private PluginState state = PluginState.LOADED;
  private PluginContext context;

  LoadedPlugin(PluginManifest manifest, AutomationPlugin instance, PluginClassLoader classLoader) {
    this.manifest = manifest;
    this.instance = instance;
    this.classLoader = classLoader;
  }

  public PluginManifest manifest() {
    return manifest;
  }

  @Override
  public String id() {
    return manifest.id();
  }

  @Override
  public PluginState state() {
    return state;
  }

  @Override
  public AutomationPlugin instance() {
    return instance;
  }

  @Override
  public PluginContext context() {
    if (context == null) {
      throw new IllegalStateException("plugin " + id() + " nao foi inicializado");
    }
    return context;
  }

  PluginClassLoader classLoader() {
    return classLoader;
  }

  public synchronized void initialize(PluginContext pluginContext) throws PluginLoadException {
    transition(PluginState.INITIALIZED);
    try {
      instance.initialize(pluginContext);
      this.context = pluginContext;
    } catch (RuntimeException error) {
      state = PluginState.FAILED;
      throw new PluginLoadException(id(), "falha em initialize()", error);
    }
  }

  public synchronized void enable() {
    transition(PluginState.ENABLED);
  }

  public synchronized void disable() {
    transition(PluginState.DISABLED);
  }

  /** Chama {@code shutdown()} e fecha o classloader. Idempotente. */
  public synchronized void unload() {
    if (state == PluginState.UNLOADED) {
      return;
    }
    try {
      instance.shutdown();
    } finally {
      try {
        classLoader.close();
      } catch (IOException ignored) {
        // fechar o jar nao pode impedir a saida do estado UNLOADED
      }
      state = PluginState.UNLOADED;
    }
  }

  private void transition(PluginState next) {
    if (!state.canTransitionTo(next)) {
      throw new IllegalStateException("transicao ilegal de " + state + " para " + next);
    }
    state = next;
  }

  @Override
  public void close() {
    unload();
  }
}
