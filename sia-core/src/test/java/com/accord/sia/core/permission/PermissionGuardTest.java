package com.accord.sia.core.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.accord.sia.api.PermissionDeniedException;
import com.accord.sia.api.PluginPermission;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PermissionGuardTest {

  @Test
  void negaPorPadrao() {
    var guard = new PermissionGuard("alpha-plugin", Set.of());

    assertThatThrownBy(() -> guard.require(PluginPermission.NETWORK))
        .isInstanceOfSatisfying(
            PermissionDeniedException.class,
            error -> assertThat(error.getMessage()).contains("network").contains("alpha-plugin"));
  }

  @Test
  void permiteOQueFoiDeclarado() {
    var guard = new PermissionGuard("alpha-plugin", Set.of(PluginPermission.STORAGE));

    assertThatCode(() -> guard.require(PluginPermission.STORAGE)).doesNotThrowAnyException();
    assertThatThrownBy(() -> guard.require(PluginPermission.SECRETS))
        .isInstanceOf(PermissionDeniedException.class);
  }

  @Test
  void probeRegistraUsoSemBloquear() {
    var probe = new PermissionProbe();

    probe.require(PluginPermission.NETWORK);
    probe.require(PluginPermission.NETWORK);
    probe.require(PluginPermission.PROCESS);

    assertThat(probe.used())
        .containsExactlyInAnyOrder(PluginPermission.NETWORK, PluginPermission.PROCESS);
    assertThat(probe.count(PluginPermission.NETWORK)).isEqualTo(2);
  }

  @Test
  void probePodeSerReiniciado() {
    var probe = new PermissionProbe();
    probe.require(PluginPermission.SECRETS);

    probe.reset();

    assertThat(probe.used()).isEmpty();
  }
}
