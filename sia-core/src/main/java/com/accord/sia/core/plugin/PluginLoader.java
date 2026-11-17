package com.accord.sia.core.plugin;

import com.accord.sia.api.AutomationPlugin;
import com.accord.sia.api.InvalidPluginException;
import com.accord.sia.api.PluginContract;
import com.accord.sia.api.PluginLoadException;
import com.accord.sia.core.manifest.ManifestLoader;
import com.accord.sia.core.manifest.PluginKind;
import com.accord.sia.core.manifest.PluginManifest;
import com.accord.sia.core.process.ProcessAutomationPlugin;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;

/**
 * Carrega um plugin {@code kind: java}.
 *
 * <p>Ordem: manifesto (T3) → classloader isolado → classe principal implementa o contrato →
 * instancia → validacao de contrato. Qualquer falha fecha o classloader, para nao vazar jar aberto.
 */
public final class PluginLoader {

  private final ManifestLoader manifestLoader = new ManifestLoader();

  public LoadedPlugin load(PluginCandidate candidate)
      throws IOException, InvalidPluginException, PluginLoadException {
    PluginManifest manifest = manifestLoader.read(candidate.manifestFile());
    if (manifest.kind() == PluginKind.PROCESS) {
      ProcessAutomationPlugin adapter =
          ProcessAutomationPlugin.start(manifest, candidate.directory());
      PluginContract.validate(adapter);
      return new LoadedPlugin(manifest, adapter, null, candidate.directory());
    }
    URL artifact = candidate.artifact().toUri().toURL();
    PluginClassLoader classLoader =
        new PluginClassLoader(
            manifest.id(), new URL[] {artifact}, AutomationPlugin.class.getClassLoader());
    try {
      AutomationPlugin instance = instantiate(classLoader, manifest);
      PluginContract.validate(instance);
      return new LoadedPlugin(manifest, instance, classLoader, candidate.directory());
    } catch (InvalidPluginException | PluginLoadException error) {
      closeQuietly(classLoader);
      throw error;
    } catch (RuntimeException error) {
      closeQuietly(classLoader);
      throw new PluginLoadException(manifest.id(), "falha ao instanciar plugin", error);
    }
  }

  private AutomationPlugin instantiate(PluginClassLoader classLoader, PluginManifest manifest)
      throws PluginLoadException {
    Class<?> type;
    try {
      type = classLoader.loadClass(manifest.main());
    } catch (ClassNotFoundException error) {
      throw new PluginLoadException(
          manifest.id(), "classe main nao encontrada: " + manifest.main(), error);
    }
    if (!AutomationPlugin.class.isAssignableFrom(type)) {
      throw new PluginLoadException(
          manifest.id(), manifest.main() + " nao implementa AutomationPlugin");
    }
    try {
      return (AutomationPlugin) type.getDeclaredConstructor().newInstance();
    } catch (NoSuchMethodException error) {
      throw new PluginLoadException(
          manifest.id(), "classe main precisa de construtor publico sem argumentos", error);
    } catch (InvocationTargetException error) {
      throw new PluginLoadException(manifest.id(), "construtor do plugin falhou", error.getCause());
    } catch (ReflectiveOperationException error) {
      throw new PluginLoadException(
          manifest.id(), "nao foi possivel instanciar a classe main", error);
    }
  }

  private static void closeQuietly(PluginClassLoader classLoader) {
    try {
      classLoader.close();
    } catch (IOException ignored) {
      // tratamento intencional: a falha original e o que importa
    }
  }
}
