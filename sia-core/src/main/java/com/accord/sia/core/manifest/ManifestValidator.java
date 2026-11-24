package com.accord.sia.core.manifest;

import com.accord.sia.api.InvalidPluginException;
import com.accord.sia.api.PluginContract;
import com.accord.sia.api.PluginPermission;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validacao estatica do {@code plugin.yml}.
 *
 * <p>Roda antes de qualquer carregamento de codigo: manifesto invalido nao chega a executar nada.
 * Cada erro aponta o campo, porque o autor do plugin precisa saber exatamente o que corrigir.
 */
public final class ManifestValidator {

  private static final Pattern MAIN_CLASS =
      Pattern.compile("^[a-z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$");
  private static final Set<String> KNOWN_OS = Set.of("windows", "linux", "macos");

  public void validate(PluginManifest manifest) throws InvalidPluginException {
    if (manifest == null) {
      throw new InvalidPluginException("plugin.yml", "manifesto vazio");
    }
    validateIdentity(manifest);
    validateKind(manifest);
    validatePermissions(manifest);
    validateDependencies(manifest);
    validateOs(manifest);
    validateUi(manifest);
  }

  /**
   * Interface propria do plugin: obrigatoria. Todo plugin entrega a propria tela; o Core so abre a
   * moldura. O caminho e relativo ao pacote e nao pode sair da pasta declarada.
   */
  private void validateUi(PluginManifest manifest) throws InvalidPluginException {
    if (manifest.ui() == null) {
      throw new InvalidPluginException(
          "ui", "obrigatorio: declare ui.entry com a pagina do plugin (ex.: ui/panel.html)");
    }
    String entry = manifest.ui().entry();
    if (isBlank(entry)) {
      throw new InvalidPluginException("ui.entry", "obrigatorio quando ui e declarado");
    }
    rejectPackageEscape("ui.entry", entry);
    if (entry.startsWith("/") || entry.contains("\\") || entry.contains(":")) {
      throw new InvalidPluginException(
          "ui.entry", "'" + entry + "' precisa ser caminho relativo com /");
    }
    if (!entry.toLowerCase(Locale.ROOT).endsWith(".html")) {
      throw new InvalidPluginException("ui.entry", "'" + entry + "' precisa apontar para .html");
    }
    if (entry.contains("?") || entry.contains("#")) {
      throw new InvalidPluginException(
          "ui.entry", "'" + entry + "' nao aceita query nem fragmento");
    }
  }

  private void validateIdentity(PluginManifest manifest) throws InvalidPluginException {
    if (!PluginContract.isValidPluginId(manifest.id())) {
      throw new InvalidPluginException(
          "id", "'" + manifest.id() + "' precisa ser minusculo, com hifen, 3-64 chars");
    }
    if (isBlank(manifest.name())) {
      throw new InvalidPluginException("name", "obrigatorio");
    }
    if (!PluginContract.isValidVersion(manifest.version())) {
      throw new InvalidPluginException("version", "'" + manifest.version() + "' nao e SemVer");
    }
    if (isBlank(manifest.apiVersion())) {
      throw new InvalidPluginException("api-version", "obrigatorio");
    }
    if (!PluginContract.isApiCompatible(manifest.apiVersion())) {
      throw new InvalidPluginException(
          "api-version", "'" + manifest.apiVersion() + "' incompativel com a API atual");
    }
  }

  private void validateKind(PluginManifest manifest) throws InvalidPluginException {
    if (manifest.kind() == null) {
      throw new InvalidPluginException("kind", "obrigatorio (java | process)");
    }
    if (manifest.kind() == PluginKind.JAVA) {
      if (isBlank(manifest.main())) {
        throw new InvalidPluginException("main", "obrigatorio no kind=java");
      }
      if (!MAIN_CLASS.matcher(manifest.main()).matches()) {
        throw new InvalidPluginException(
            "main", "'" + manifest.main() + "' nao e classe qualificada");
      }
      if (!manifest.command().isEmpty()) {
        throw new InvalidPluginException("command", "so faz sentido no kind=process");
      }
      return;
    }
    validateProcessEntrypoint(manifest);
  }

  private void validateProcessEntrypoint(PluginManifest manifest) throws InvalidPluginException {
    if (manifest.main() != null) {
      throw new InvalidPluginException("main", "so faz sentido no kind=java");
    }
    if (manifest.command().isEmpty()) {
      throw new InvalidPluginException("command", "obrigatorio no kind=process");
    }
    if (!PluginManifest.DEFAULT_PROCESS_PROTOCOL.equals(manifest.protocol())) {
      throw new InvalidPluginException(
          "protocol",
          "protocolo '"
              + manifest.protocol()
              + "' nao suportado; use "
              + PluginManifest.DEFAULT_PROCESS_PROTOCOL);
    }
    for (int index = 0; index < manifest.command().size(); index++) {
      String part = manifest.command().get(index);
      String field = "command[" + index + "]";
      if (isBlank(part)) {
        throw new InvalidPluginException(field, "vazio");
      }
      if (part.contains("\n") || part.contains("\r") || part.indexOf('\0') >= 0) {
        throw new InvalidPluginException(field, "contem caractere de controle");
      }
      if (index == 0) {
        validateRelativeEntrypoint(part);
      }
      rejectPackageEscape(field, part);
    }
  }

  /** Nenhum argumento pode sair do pacote do plugin com {@code ..}. */
  private void rejectPackageEscape(String field, String value) throws InvalidPluginException {
    for (Path part : Path.of(value)) {
      if ("..".equals(part.toString())) {
        throw new InvalidPluginException(field, "nao pode sair do pacote do plugin (..)");
      }
    }
  }

  /**
   * O executavel aponta para dentro do pacote do plugin. Caminho absoluto ou {@code ..} deixaria o
   * manifesto escolher qualquer binario da maquina.
   */
  private void validateRelativeEntrypoint(String executable) throws InvalidPluginException {
    Path path = Path.of(executable);
    if (path.isAbsolute() || executable.startsWith("/") || executable.contains(":")) {
      throw new InvalidPluginException(
          "command[0]", "precisa ser caminho relativo dentro do pacote do plugin");
    }
    for (Path part : path) {
      if ("..".equals(part.toString())) {
        throw new InvalidPluginException("command[0]", "nao pode sair do pacote do plugin (..)");
      }
    }
  }

  private void validatePermissions(PluginManifest manifest) throws InvalidPluginException {
    Set<String> seen = new HashSet<>();
    for (String permission : manifest.permissions()) {
      if (permission == null || permission.isBlank()) {
        throw new InvalidPluginException("permissions", "entrada vazia");
      }
      if (!seen.add(permission.toLowerCase(Locale.ROOT))) {
        throw new InvalidPluginException("permissions", "duplicada: " + permission);
      }
      try {
        PluginPermission.fromWireName(permission);
      } catch (IllegalArgumentException error) {
        throw new InvalidPluginException(
            "permissions",
            "'"
                + permission
                + "' desconhecida; validas: network, storage, secrets, process, events");
      }
    }
  }

  private void validateDependencies(PluginManifest manifest) throws InvalidPluginException {
    List<String> dependencies = manifest.dependencies();
    Set<String> seen = new HashSet<>();
    for (String dependency : dependencies) {
      if (!PluginContract.isValidPluginId(dependency)) {
        throw new InvalidPluginException("dependencies", "'" + dependency + "' nao e id de plugin");
      }
      if (dependency.equals(manifest.id())) {
        throw new InvalidPluginException("dependencies", "plugin nao pode depender de si mesmo");
      }
      if (!seen.add(dependency)) {
        throw new InvalidPluginException("dependencies", "duplicada: " + dependency);
      }
    }
  }

  private void validateOs(PluginManifest manifest) throws InvalidPluginException {
    for (String os : manifest.requiresOs()) {
      if (os == null || !KNOWN_OS.contains(os.toLowerCase(Locale.ROOT))) {
        throw new InvalidPluginException(
            "requires-os", "'" + os + "' desconhecido; validos: windows, linux, macos");
      }
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
