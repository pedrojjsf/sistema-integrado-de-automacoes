package com.accord.sia.core.plugin;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

/**
 * Classloader do plugin: filho-primeiro, exceto pelo que e compartilhado com o Core.
 *
 * <p>Isso permite que o plugin traga a propria versao de uma biblioteca sem conflitar com outro
 * plugin, ao mesmo tempo em que {@code sia-api} e {@code slf4j} precisam ser as mesmas classes nos
 * dois lados — senao o Core nao reconheceria a implementacao de {@code AutomationPlugin}.
 *
 * <p>Nao e sandbox: e isolamento de namespaces. Quem quiser isolamento de verdade usa {@code kind:
 * process}.
 */
public final class PluginClassLoader extends URLClassLoader {

  private static final List<String> SHARED_PREFIXES =
      List.of("java.", "javax.", "jdk.", "sun.", "com.sun.", "com.accord.sia.api.", "org.slf4j.");

  public PluginClassLoader(String pluginId, URL[] urls, ClassLoader parent) {
    super("sia-plugin:" + pluginId, urls, parent);
  }

  @Override
  protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    if (isShared(name)) {
      return super.loadClass(name, resolve);
    }
    synchronized (getClassLoadingLock(name)) {
      Class<?> loaded = findLoadedClass(name);
      if (loaded == null) {
        try {
          loaded = findClass(name);
        } catch (ClassNotFoundException notInPlugin) {
          loaded = super.loadClass(name, false);
        }
      }
      if (resolve) {
        resolveClass(loaded);
      }
      return loaded;
    }
  }

  private static boolean isShared(String name) {
    for (String prefix : SHARED_PREFIXES) {
      if (name.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void close() throws IOException {
    super.close();
  }
}
