package com.accord.sia.core.secrets;

import com.accord.sia.api.PluginPermission;
import com.accord.sia.api.SecretAccess;
import com.accord.sia.core.process.PermissionCheck;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Segredos vindos de variaveis de ambiente, escopados por plugin.
 *
 * <p>O nome do segredo nao e global: {@code smtp.password} do plugin {@code x} e {@code
 * SIA_SECRET_X_SMTP_PASSWORD}. Assim, mesmo sem cofre, um plugin nao le o segredo de outro — o
 * namespace e estrutural, nao uma convencao de nomes.
 */
public final class EnvSecretAccess implements SecretAccess {

  private static final String PREFIX = "SIA_SECRET_";

  private final String pluginId;
  private final PermissionCheck permissions;
  private final Function<String, String> environment;

  public EnvSecretAccess(String pluginId, PermissionCheck permissions) {
    this(pluginId, permissions, System::getenv);
  }

  EnvSecretAccess(String pluginId, PermissionCheck permissions, Map<String, String> environment) {
    this(pluginId, permissions, environment::get);
  }

  EnvSecretAccess(
      String pluginId, PermissionCheck permissions, Function<String, String> environment) {
    this.pluginId = pluginId;
    this.permissions = permissions;
    this.environment = environment;
  }

  @Override
  public Optional<String> get(String name) {
    permissions.require(PluginPermission.SECRETS);
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("nome de segredo vazio");
    }
    return Optional.ofNullable(environment.apply(envKey(name)));
  }

  public static String envKey(String pluginId, String name) {
    return PREFIX + sanitize(pluginId) + "_" + sanitize(name);
  }

  String envKey(String name) {
    return envKey(pluginId, name);
  }

  private static String sanitize(String value) {
    return value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
  }
}
