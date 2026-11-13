package com.accord.sia.core.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.accord.sia.api.InvalidPluginException;
import com.accord.sia.api.PluginLoadException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginLoaderTest {

  @TempDir Path temp;

  private final PluginLoader loader = new PluginLoader();
  private final List<LoadedPlugin> opened = new ArrayList<>();
  private Path pluginsDirectory;

  @BeforeEach
  void packageFixtures() throws IOException {
    pluginsDirectory = temp.resolve("plugins");
    PluginFixtures.packagePlugin(temp, "alpha", pluginsDirectory);
    PluginFixtures.packagePlugin(temp, "beta", pluginsDirectory);
  }

  @AfterEach
  void closePlugins() {
    for (LoadedPlugin plugin : opened) {
      try {
        plugin.unload();
      } catch (RuntimeException ignored) {
        // teste nao deve falhar por causa do fechamento
      }
    }
    opened.clear();
  }

  @Test
  void descobrePluginsEmpacotados() throws Exception {
    List<PluginCandidate> candidates = new PluginDiscovery().discover(pluginsDirectory);

    assertThat(candidates).hasSize(2);
    assertThat(candidates)
        .extracting(candidate -> candidate.artifact().getFileName().toString())
        .containsOnly("plugin.jar");
  }

  @Test
  void ignoraPastaSemManifesto() throws Exception {
    Files.createDirectories(pluginsDirectory.resolve("sobra-de-copia"));

    assertThat(new PluginDiscovery().discover(pluginsDirectory)).hasSize(2);
  }

  @Test
  void carregaPluginJavaEValidaContrato() throws Exception {
    LoadedPlugin plugin = load("alpha");

    assertThat(plugin.id()).isEqualTo("alpha-plugin");
    assertThat(plugin.state()).isEqualTo(PluginState.LOADED);
    assertThat(plugin.instance().getClass().getName()).isEqualTo("testplugin.alpha.AlphaPlugin");
  }

  @Test
  void isolaClassesEntrePlugins() throws Exception {
    LoadedPlugin alpha = load("alpha");
    load("beta");

    assertThat(Class.forName("testplugin.alpha.AlphaPlugin", false, alpha.classLoader()).getName())
        .isEqualTo("testplugin.alpha.AlphaPlugin");

    assertThatThrownBy(
            () -> Class.forName("testplugin.beta.BetaSecret", false, alpha.classLoader()))
        .isInstanceOf(ClassNotFoundException.class);
  }

  @Test
  void rejeitaPluginSemAutomacao() {
    assertThatThrownBy(
            () -> {
              Path broken = PluginFixtures.packagePlugin(temp, "broken", pluginsDirectory);
              loader.load(candidateOf(broken));
            })
        .isInstanceOfSatisfying(
            InvalidPluginException.class,
            error -> assertThat(error.field()).isEqualTo("automations"));
  }

  @Test
  void rejeitaKindProcessEnquantoNaoHaHost() throws Exception {
    Path directory = Files.createDirectories(pluginsDirectory.resolve("process-plugin"));
    Files.writeString(
        directory.resolve("plugin.yml"),
        """
        id: process-plugin
        name: Process
        version: 1.0.0
        api-version: "1.0"
        kind: process
        command: ["python", "main.py"]
        """);
    Files.write(directory.resolve("plugin.jar"), new byte[] {0});

    assertThatThrownBy(() -> loader.load(candidateOf(directory)))
        .isInstanceOf(PluginLoadException.class)
        .hasMessageContaining("kind=process");
  }

  @Test
  void unloadExecutaShutdownEFechaJar() throws Exception {
    Path marker = temp.resolve("shutdown.marker");
    System.setProperty("sia.test.shutdownMarker", marker.toString());
    try {
      LoadedPlugin plugin = load("alpha");
      plugin.initialize(TestContexts.noop(plugin.id()));
      plugin.enable();
      plugin.unload();

      assertThat(marker).exists();
      assertThat(plugin.state()).isEqualTo(PluginState.UNLOADED);
      assertThatThrownBy(
              () -> Class.forName("testplugin.alpha.AlphaHelper", false, plugin.classLoader()))
          .isInstanceOf(ClassNotFoundException.class);
    } finally {
      System.clearProperty("sia.test.shutdownMarker");
    }
  }

  @Test
  void transicaoIlegalFalhaAlto() throws Exception {
    LoadedPlugin plugin = load("alpha");

    assertThatThrownBy(plugin::disable)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("LOADED");
  }

  @Test
  void registroRecusaDuplicata() throws Exception {
    PluginRegistry registry = new PluginRegistry();
    registry.register(load("alpha"));
    LoadedPlugin second = load("alpha");

    assertThatThrownBy(() -> registry.register(second))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("duplicado");
    assertThat(registry.size()).isEqualTo(1);
  }

  @Test
  void registroDescarregaTodos() throws Exception {
    PluginRegistry registry = new PluginRegistry();
    registry.register(load("alpha"));
    registry.register(load("beta"));

    registry.unloadAll();

    assertThat(registry.size()).isZero();
  }

  private LoadedPlugin load(String fixture) throws Exception {
    LoadedPlugin plugin = loader.load(candidateOf(pluginsDirectory.resolve(fixture)));
    opened.add(plugin);
    return plugin;
  }

  private static PluginCandidate candidateOf(Path directory) {
    List<Path> jars;
    try (var files = Files.list(directory)) {
      jars = files.filter(path -> path.toString().endsWith(".jar")).toList();
    } catch (IOException error) {
      throw new IllegalStateException(error);
    }
    return new PluginCandidate(
        directory, directory.resolve(PluginDiscovery.MANIFEST_NAME), jars.get(0));
  }
}
