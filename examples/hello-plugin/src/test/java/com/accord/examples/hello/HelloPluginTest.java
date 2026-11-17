package com.accord.examples.hello;

import static org.assertj.core.api.Assertions.assertThat;

import com.accord.sia.testkit.Permissions;
import com.accord.sia.testkit.PluginTestKit;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HelloPluginTest {

  @Test
  void respondeEmPortuguesPorPadrao() {
    try (PluginTestKit kit = PluginTestKit.start(new HelloPlugin(), Permissions.none())) {
      var resultado = kit.run("hello.say", Map.of("nome", "Camila"));

      resultado.assertSuccess();
      assertThat(resultado.outputs()).containsEntry("saudacao", "Ola, Camila");
    }
  }

  @Test
  void respeitaIdiomaInformado() {
    try (PluginTestKit kit = PluginTestKit.start(new HelloPlugin(), Permissions.none())) {
      var resultado = kit.run("hello.say", Map.of("nome", "Camila", "idioma", "en"));

      resultado.assertSuccess();
      assertThat(resultado.outputs()).containsEntry("saudacao", "Hello, Camila");
    }
  }

  @Test
  void pluginCumpreOContrato() throws Exception {
    com.accord.sia.api.PluginContract.validate(new HelloPlugin());
  }
}
