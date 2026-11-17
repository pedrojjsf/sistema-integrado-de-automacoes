package com.accord.sia.core.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.accord.sia.api.PermissionDeniedException;
import com.accord.sia.api.PluginPermission;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EnvSecretAccessTest {

  private static final Map<String, String> ENVIRONMENT =
      Map.of(
          "SIA_SECRET_ALPHA_PLUGIN_SMTP_PASSWORD", "senha-alpha",
          "SIA_SECRET_BETA_PLUGIN_SMTP_PASSWORD", "senha-beta");

  @Test
  void leSegredoEscopadoAoPlugin() {
    var access = access("alpha-plugin", Set.of(PluginPermission.SECRETS));

    assertThat(access.get("smtp.password")).contains("senha-alpha");
  }

  @Test
  void naoLêSegredoDeOutroPlugin() {
    var beta = access("beta-plugin", Set.of(PluginPermission.SECRETS));

    assertThat(beta.get("smtp.password")).contains("senha-beta");
    assertThat(beta.envKey("smtp.password")).isNotEqualTo("SIA_SECRET_ALPHA_PLUGIN_SMTP_PASSWORD");
  }

  @Test
  void segredoAusenteViraVazio() {
    var access = access("alpha-plugin", Set.of(PluginPermission.SECRETS));

    assertThat(access.get("inexistente")).isEmpty();
  }

  @Test
  void exigePermissaoDeSegredos() {
    var access = access("alpha-plugin", Set.of(PluginPermission.STORAGE));

    assertThatThrownBy(() -> access.get("smtp.password"))
        .isInstanceOf(PermissionDeniedException.class);
  }

  @Test
  void chaveDeAmbienteNormalizaNome() {
    assertThat(EnvSecretAccess.envKey("alpha-plugin", "smtp.password"))
        .isEqualTo("SIA_SECRET_ALPHA_PLUGIN_SMTP_PASSWORD");
  }

  private static EnvSecretAccess access(String pluginId, Set<PluginPermission> granted) {
    return new EnvSecretAccess(
        pluginId,
        permission -> {
          if (!granted.contains(permission)) {
            throw new PermissionDeniedException(permission.wireName(), pluginId);
          }
        },
        ENVIRONMENT);
  }
}
