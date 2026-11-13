package com.accord.sia.core.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.accord.sia.api.InvalidPluginException;
import com.accord.sia.api.PluginPermission;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManifestLoaderTest {

  private final ManifestLoader loader = new ManifestLoader();

  private static final String JAVA_MANIFEST =
      """
      id: demo-plugin
      name: Demo
      version: 1.0.0
      api-version: "1.0"
      author: Accord
      kind: java
      main: com.accord.plugins.demo.DemoPlugin
      permissions: [storage]
      """;

  private static final String PROCESS_MANIFEST =
      """
      id: receitanet-bx
      name: Receitanet BX
      version: 1.2.0
      api-version: "1.0"
      author: Accord
      description: Baixa arquivos do Receitanet BX
      kind: process
      command: ["java", "-Djava.awt.headless=true", "-cp", "app/receitanetbx-bot.jar", "numberia.bot.Inicio"]
      permissions: [process]
      requires-os: [windows]
      """;

  @Test
  void aceitaManifestoJava() throws InvalidPluginException {
    PluginManifest manifest = loader.parse(JAVA_MANIFEST);

    assertThat(manifest.id()).isEqualTo("demo-plugin");
    assertThat(manifest.kind()).isEqualTo(PluginKind.JAVA);
    assertThat(manifest.main()).isEqualTo("com.accord.plugins.demo.DemoPlugin");
    assertThat(manifest.grantedPermissions()).containsExactly(PluginPermission.STORAGE);
  }

  @Test
  void aceitaManifestoProcessComProtocoloPadrao() throws InvalidPluginException {
    PluginManifest manifest = loader.parse(PROCESS_MANIFEST);

    assertThat(manifest.kind()).isEqualTo(PluginKind.PROCESS);
    assertThat(manifest.protocol()).isEqualTo(PluginManifest.DEFAULT_PROCESS_PROTOCOL);
    assertThat(manifest.command()).hasSize(5);
    assertThat(manifest.requiresOs()).containsExactly("windows");
    assertThat(manifest.grantedPermissions()).containsExactly(PluginPermission.PROCESS);
  }

  @Test
  void leManifestoDoDisco(@TempDir Path dir) throws IOException, InvalidPluginException {
    Path file = dir.resolve("plugin.yml");
    Files.writeString(file, PROCESS_MANIFEST);

    assertThat(loader.read(file).id()).isEqualTo("receitanet-bx");
  }

  @Test
  void rejeitaCampoDesconhecido() {
    assertField(
        """
        id: demo-plugin
        name: Demo
        version: 1.0.0
        api-version: "1.0"
        kind: java
        main: com.accord.Demo
        permisions: [storage]
        """,
        "plugin.yml");
  }

  @Test
  void rejeitaIdInvalido() {
    assertField(JAVA_MANIFEST.replace("id: demo-plugin", "id: Email-Demo"), "id");
  }

  @Test
  void rejeitaVersionForaDeSemver() {
    assertField(JAVA_MANIFEST.replace("version: 1.0.0", "version: 1.0"), "version");
  }

  @Test
  void rejeitaApiVersionIncompativel() {
    assertField(
        JAVA_MANIFEST.replace("api-version: \"1.0\"", "api-version: \"2.0\""), "api-version");
  }

  @Test
  void rejeitaSemKind() {
    assertField(JAVA_MANIFEST.replace("kind: java", ""), "kind");
  }

  @Test
  void rejeitaJavaSemMain() {
    assertField(JAVA_MANIFEST.replace("main: com.accord.plugins.demo.DemoPlugin", ""), "main");
  }

  @Test
  void rejeitaJavaComCommand() {
    assertField(JAVA_MANIFEST + "command: [\"java\"]\n", "command");
  }

  @Test
  void rejeitaProcessSemCommand() {
    assertField(
        PROCESS_MANIFEST.replace(
            "command: [\"java\", \"-Djava.awt.headless=true\", \"-cp\", \"app/receitanetbx-bot.jar\", \"numberia.bot.Inicio\"]",
            ""),
        "command");
  }

  @Test
  void rejeitaProcessComCaminhoAbsoluto() {
    assertField(
        PROCESS_MANIFEST.replace("\"java\"", "\"C:/Program Files/Java/bin/java\""), "command[0]");
  }

  @Test
  void rejeitaProcessComEscapadaDePacote() {
    assertField(PROCESS_MANIFEST.replace("\"java\"", "\"../evil\""), "command[0]");
  }

  @Test
  void rejeitaPermissaoDesconhecida() {
    assertField(
        PROCESS_MANIFEST.replace("permissions: [process]", "permissions: [network, admin]"),
        "permissions");
  }

  @Test
  void rejeitaDependenciaDeSiMesmo() {
    assertField(PROCESS_MANIFEST + "dependencies: [receitanet-bx]\n", "dependencies");
  }

  @Test
  void rejeitaSoDesconhecido() {
    assertField(
        PROCESS_MANIFEST.replace("requires-os: [windows]", "requires-os: [solaris]"),
        "requires-os");
  }

  @Test
  void rejeitaProtocoloNaoSuportado() {
    assertField(PROCESS_MANIFEST + "protocol: http-jsonrpc\n", "protocol");
  }

  @Test
  void rejeitaManifestoVazio() {
    assertField("   ", "plugin.yml");
  }

  private void assertField(String yaml, String expectedField) {
    assertThatThrownBy(() -> loader.parse(yaml))
        .isInstanceOfSatisfying(
            InvalidPluginException.class,
            error -> assertThat(error.field()).isEqualTo(expectedField));
  }
}
