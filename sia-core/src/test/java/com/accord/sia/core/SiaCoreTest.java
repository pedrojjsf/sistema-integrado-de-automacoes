package com.accord.sia.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.accord.sia.api.Actor;
import com.accord.sia.api.Parameters;
import com.accord.sia.api.RunId;
import com.accord.sia.core.config.SiaConfig;
import com.accord.sia.core.plugin.PluginFixtures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SiaCoreTest {

  @TempDir Path temp;

  private Path pluginsDirectory;
  private SiaCore core;

  @BeforeEach
  void setUp() throws Exception {
    pluginsDirectory = temp.resolve("plugins");
    PluginFixtures.packagePlugin(temp, "alpha", pluginsDirectory);
    core = SiaCore.start(config());
  }

  @AfterEach
  void tearDown() {
    if (core != null) {
      core.close();
    }
  }

  private SiaConfig config() {
    return new SiaConfig(
        new SiaConfig.Server("127.0.0.1", 0),
        new SiaConfig.Database(temp.resolve("sia.db").toString()),
        new SiaConfig.Plugins(pluginsDirectory.toString()),
        new SiaConfig.Auth("dev", Map.of()),
        Map.of());
  }

  @Test
  void carregaPluginDoDiretorioEExecutaAutomacao() {
    assertThat(core.plugins()).hasSize(1);
    assertThat(core.automations()).extracting(descriptor -> descriptor.id()).contains("alpha.run");

    var outcome = core.execute("alpha.run", Parameters.empty(), Actor.user("sub-1", "Analista"));

    assertThat(outcome.status().name()).isEqualTo("SUCCESS");
  }

  @Test
  void mesmaChaveDeIdempotenciaDevolveOMesmoRun() {
    Actor actor = Actor.user("sub-1", "Analista");

    RunId first = core.submit("alpha.run", Parameters.empty(), actor, "api", "chave-1");
    RunId second = core.submit("alpha.run", Parameters.empty(), actor, "api", "chave-1");

    assertThat(second).isEqualTo(first);
  }

  @Test
  void chavesDiferentesCriamRunsDiferentes() {
    Actor actor = Actor.user("sub-1", "Analista");

    RunId first = core.submit("alpha.run", Parameters.empty(), actor, "api", "chave-1");
    RunId second = core.submit("alpha.run", Parameters.empty(), actor, "api", "chave-2");

    assertThat(second).isNotEqualTo(first);
  }

  @Test
  void desabilitaEHabilitaPlugin() {
    core.disablePlugin("alpha-plugin");
    assertThat(core.plugin("alpha-plugin").orElseThrow().state().name()).isEqualTo("DISABLED");

    core.enablePlugin("alpha-plugin");
    assertThat(core.plugin("alpha-plugin").orElseThrow().state().name()).isEqualTo("ENABLED");
  }

  @Test
  void recarregaPluginDoDisco() throws Exception {
    var reloaded = core.reloadPlugin("alpha-plugin");

    assertThat(reloaded.state().name()).isEqualTo("ENABLED");
    assertThat(core.plugins()).hasSize(1);
  }

  @Test
  void instalaPluginAPartirDoJar() throws Exception {
    Path beta = PluginFixtures.packagePlugin(temp, "beta", temp.resolve("instalaveis"));
    Path jar =
        Files.list(beta).filter(path -> path.toString().endsWith(".jar")).findFirst().orElseThrow();

    var installed = core.installPlugin(jar);

    assertThat(installed.id()).isEqualTo("beta-plugin");
    assertThat(core.plugins()).hasSize(2);
    assertThat(core.automations()).extracting(descriptor -> descriptor.id()).contains("beta.run");
  }

  @Test
  void validaPacoteSemInstalar() throws Exception {
    Path beta = PluginFixtures.packagePlugin(temp, "beta", temp.resolve("instalaveis"));
    Path jar =
        Files.list(beta).filter(path -> path.toString().endsWith(".jar")).findFirst().orElseThrow();

    var validation = core.validatePlugin(jar);

    assertThat(validation.id()).isEqualTo("beta-plugin");
    assertThat(validation.mainClassPresent()).isTrue();
    assertThat(core.plugins()).hasSize(1);
  }

  @Test
  void estadoDesabilitadoSobreviveAoRestart() throws Exception {
    core.disablePlugin("alpha-plugin");
    core.close();

    core = SiaCore.start(config());

    assertThat(core.plugin("alpha-plugin").orElseThrow().state().name()).isEqualTo("DISABLED");
  }

  @Test
  void estadoHabilitadoEhPersistido() throws Exception {
    core.disablePlugin("alpha-plugin");
    core.enablePlugin("alpha-plugin");
    core.close();

    core = SiaCore.start(config());

    assertThat(core.plugin("alpha-plugin").orElseThrow().state().name()).isEqualTo("ENABLED");
  }

  @Test
  void desinstalaPluginRemovendoArquivos() throws Exception {
    Path directory = pluginsDirectory.resolve("alpha");
    assertThat(directory).exists();

    core.uninstallPlugin("alpha-plugin");

    assertThat(core.plugin("alpha-plugin")).isEmpty();
    assertThat(directory).doesNotExist();
  }

  @Test
  void recusaSegundoCoreNoMesmoBanco() throws Exception {
    assertThatThrownBy(() -> SiaCore.start(config()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ja existe um SIA rodando");
  }

  @Test
  void lockEhRemovidoAoFechar() {
    SiaCore local = core;
    core = null;
    local.close();

    assertThat(Files.exists(Path.of(temp.resolve("sia.db").toString() + ".lock"))).isFalse();
  }
}
