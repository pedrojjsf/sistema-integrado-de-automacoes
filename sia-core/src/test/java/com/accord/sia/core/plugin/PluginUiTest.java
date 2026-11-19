package com.accord.sia.core.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginUiTest {

  @TempDir Path temp;

  @Test
  void serveOArquivoDeclaradoNoManifesto() throws Exception {
    LoadedPlugin plugin = loadPanelPlugin();

    PluginUi.Resource resource = PluginUi.read(plugin, "ui/panel.html");

    assertThat(resource.contentType()).isEqualTo("text/html; charset=utf-8");
    assertThat(new String(resource.content(), StandardCharsets.UTF_8)).contains("Painel do plugin");
    plugin.unload();
  }

  @Test
  void recusaSaidaDoDiretorio() throws Exception {
    LoadedPlugin plugin = loadPanelPlugin();

    assertThatThrownBy(() -> PluginUi.read(plugin, "../plugin.yml"))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("invalido");
    assertThatThrownBy(() -> PluginUi.read(plugin, "ui/../../plugin.yml"))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("invalido");
    plugin.unload();
  }

  @Test
  void recusaArquivoForaDaPastaDeUi() throws Exception {
    LoadedPlugin plugin = loadPanelPlugin();

    assertThatThrownBy(() -> PluginUi.read(plugin, "plugin.yml"))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("so pode servir arquivos de ui/");
    plugin.unload();
  }

  @Test
  void recusaPluginSemInterface() throws Exception {
    Path alpha = PluginFixtures.packagePlugin(temp, "alpha", temp.resolve("plugins"));
    LoadedPlugin plugin =
        new PluginLoader()
            .load(
                new PluginCandidate(
                    alpha, alpha.resolve("plugin.yml"), alpha.resolve("plugin.jar")));

    // a interface agora e obrigatoria; o que resta proibido e sair da pasta ui/
    assertThatThrownBy(() -> PluginUi.read(plugin, "outra/pagina.html"))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("so pode servir arquivos de ui/");
    plugin.unload();
  }

  @Test
  void defineTipoDeConteudoPorExtensao() {
    assertThat(PluginUi.contentType("ui/panel.html")).startsWith("text/html");
    assertThat(PluginUi.contentType("ui/painel.css")).startsWith("text/css");
    assertThat(PluginUi.contentType("ui/painel.js")).startsWith("text/javascript");
    assertThat(PluginUi.contentType("ui/dados.json")).startsWith("application/json");
    assertThat(PluginUi.contentType("ui/logo.svg")).isEqualTo("image/svg+xml");
    assertThat(PluginUi.contentType("ui/logo.png")).isEqualTo("image/png");
    assertThat(PluginUi.contentType("ui/fonte.woff2")).isEqualTo("font/woff2");
    assertThat(PluginUi.contentType("ui/arquivo.bin")).isEqualTo("application/octet-stream");
  }

  @Test
  void recusaPedidoVazio() throws Exception {
    LoadedPlugin plugin = loadPanelPlugin();

    assertThatThrownBy(() -> PluginUi.read(plugin, "  "))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("vazio");
    plugin.unload();
  }

  @Test
  void recusaArquivoInexistente() throws Exception {
    LoadedPlugin plugin = loadPanelPlugin();

    assertThatThrownBy(() -> PluginUi.read(plugin, "ui/nao-existe.html"))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("nao encontrado");
    plugin.unload();
  }

  private LoadedPlugin loadPanelPlugin() throws Exception {
    Path directory = PluginFixtures.packagePlugin(temp, "panel", temp.resolve("plugins"));
    return new PluginLoader()
        .load(
            new PluginCandidate(
                directory, directory.resolve("plugin.yml"), directory.resolve("plugin.jar")));
  }
}
