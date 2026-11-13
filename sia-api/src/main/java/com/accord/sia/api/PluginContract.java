package com.accord.sia.api;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validacao do contrato que um plugin precisa cumprir para ser aceito.
 *
 * <p>Roda no carregamento e na validacao de artefato. Erros apontam o campo exato, porque mensagem
 * generica ("plugin invalido") nao ajuda quem esta escrevendo plugin.
 */
public final class PluginContract {

  private static final Pattern PLUGIN_ID = Pattern.compile("^[a-z][a-z0-9-]{2,63}$");
  private static final Pattern AUTOMATION_ID =
      Pattern.compile("^[a-z][a-z0-9-]*\\.[a-z][a-z0-9-]*$");
  private static final Pattern PARAM_NAME = Pattern.compile("^[a-z][a-zA-Z0-9]{0,31}$");
  private static final Pattern SEMVER = Pattern.compile("^\\d+\\.\\d+\\.\\d+(-[0-9A-Za-z.-]+)?$");

  private PluginContract() {}

  public static void validate(AutomationPlugin plugin) throws InvalidPluginException {
    if (plugin == null) {
      throw new InvalidPluginException("plugin", "implementacao nula");
    }
    validate(plugin.metadata());
    validateAutomations(plugin.automations());
  }

  public static void validate(PluginMetadata metadata) throws InvalidPluginException {
    if (metadata == null) {
      throw new InvalidPluginException("metadata", "metadata() devolveu nulo");
    }
    requireMatch(metadata.id(), "metadata.id", PLUGIN_ID, "minusculo, com hifen, 3-64 chars");
    requireText(metadata.name(), "metadata.name");
    requireMatch(metadata.version(), "metadata.version", SEMVER, "SemVer (x.y.z)");
    requireText(metadata.apiVersion(), "metadata.apiVersion");
    if (!isApiCompatible(metadata.apiVersion())) {
      throw new InvalidPluginException(
          "metadata.apiVersion",
          "apiVersion " + metadata.apiVersion() + " incompativel com " + SiaApi.API_VERSION);
    }
  }

  public static void validateAutomations(List<Automation> automations)
      throws InvalidPluginException {
    if (automations == null || automations.isEmpty()) {
      throw new InvalidPluginException(
          "automations", "plugin precisa expor ao menos uma automacao");
    }
    Set<String> seen = new HashSet<>();
    for (Automation automation : automations) {
      if (automation == null) {
        throw new InvalidPluginException("automations", "entrada nula");
      }
      AutomationDescriptor descriptor = automation.describe();
      validate(descriptor);
      if (!descriptor.id().equals(automation.id())) {
        throw new InvalidPluginException(
            "automation[" + descriptor.id() + "].id", "describe().id() diverge de id()");
      }
      if (!seen.add(descriptor.id())) {
        throw new InvalidPluginException("automations[" + descriptor.id() + "]", "id duplicado");
      }
    }
  }

  public static void validate(AutomationDescriptor descriptor) throws InvalidPluginException {
    if (descriptor == null) {
      throw new InvalidPluginException("automations", "descriptor nulo");
    }
    requireMatch(descriptor.id(), "automation.id", AUTOMATION_ID, "formato plugin.acao");
    requireText(descriptor.name(), "automation[" + descriptor.id() + "].name");
    Set<String> params = new HashSet<>();
    for (ParamSpec spec : descriptor.params()) {
      validate(spec);
      if (!params.add(spec.name())) {
        throw new InvalidPluginException(
            "automation[" + descriptor.id() + "].params", "parametro duplicado: " + spec.name());
      }
    }
  }

  public static void validate(ParamSpec spec) throws InvalidPluginException {
    if (spec == null) {
      throw new InvalidPluginException("param", "ParamSpec nulo");
    }
    requireMatch(spec.name(), "param.name", PARAM_NAME, "camelCase, ate 32 chars");
    if (spec.sensitive() && spec.defaultValue() != null) {
      throw new InvalidPluginException(
          "param[" + spec.name() + "].defaultValue",
          "parametro sensivel nao pode declarar valor padrao");
    }
  }

  public static boolean isValidPluginId(String id) {
    return id != null && PLUGIN_ID.matcher(id).matches();
  }

  public static boolean isValidVersion(String version) {
    return version != null && SEMVER.matcher(version).matches();
  }

  /** Mesma major da API corrente significa compativel. */
  public static boolean isApiCompatible(String apiVersion) {
    if (apiVersion == null || apiVersion.isBlank()) {
      return false;
    }
    String expectedMajor = SiaApi.API_VERSION.split("\\.")[0];
    return apiVersion.split("\\.")[0].equals(expectedMajor);
  }

  private static void requireText(String value, String field) throws InvalidPluginException {
    if (value == null || value.isBlank()) {
      throw new InvalidPluginException(field, "obrigatorio e nao pode ser vazio");
    }
  }

  private static void requireMatch(String value, String field, Pattern pattern, String rule)
      throws InvalidPluginException {
    requireText(value, field);
    if (!pattern.matcher(value).matches()) {
      throw new InvalidPluginException(field, "'" + value + "' nao segue " + rule);
    }
  }
}
