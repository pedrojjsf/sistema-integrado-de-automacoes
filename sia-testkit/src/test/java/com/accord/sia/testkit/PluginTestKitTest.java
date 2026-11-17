package com.accord.sia.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.accord.sia.api.Automation;
import com.accord.sia.api.AutomationContext;
import com.accord.sia.api.AutomationDescriptor;
import com.accord.sia.api.AutomationPlugin;
import com.accord.sia.api.AutomationResult;
import com.accord.sia.api.ParamSpec;
import com.accord.sia.api.ParamType;
import com.accord.sia.api.PluginContext;
import com.accord.sia.api.PluginMetadata;
import com.accord.sia.api.PluginPermission;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PluginTestKitTest {

  @Test
  void executaAutomacaoComSucesso() {
    try (PluginTestKit kit = PluginTestKit.start(new FakePlugin(), Permissions.none())) {
      RunResult result = kit.run("fake.eco", Map.of("texto", "ola"));

      result.assertSuccess();
      assertThat(result.outputs()).containsEntry("eco", "ola");
    }
  }

  @Test
  void negaPermissaoNaoConcedida() {
    try (PluginTestKit kit = PluginTestKit.start(new FakePlugin(), Permissions.none())) {
      RunResult result = kit.run("fake.grava");

      result.assertFailure("PERMISSION_DENIED");
      assertThat(result.message()).contains("storage");
    }
  }

  @Test
  void permiteQuandoAPermissaoEhConcedida() {
    try (PluginTestKit kit =
        PluginTestKit.start(new FakePlugin(), Permissions.of(PluginPermission.STORAGE))) {
      kit.run("fake.grava").assertSuccess();
    }
  }

  @Test
  void naoVazaParametroSensivelNoLog() {
    try (PluginTestKit kit = PluginTestKit.start(new FakePlugin(), Permissions.none())) {
      RunResult result = kit.run("fake.loga", Map.of("segredo", "senha-super-secreta"));

      result.assertSuccess().assertNoSensitiveData("senha-super-secreta");
      assertThat(result.logs()).anyMatch(line -> line.contains("***"));
    }
  }

  @Test
  void automacaoDesconhecidaFalhaAlto() {
    try (PluginTestKit kit = PluginTestKit.start(new FakePlugin(), Permissions.none())) {
      assertThatThrownBy(() -> kit.run("fake.naoexiste"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("fake.naoexiste");
    }
  }

  @Test
  void configuracaoDoTesteEstaDisponivelNoContexto() {
    try (PluginTestKit kit =
        PluginTestKit.start(new FakePlugin(), Permissions.none(), Map.of("destino", "pasta"))) {
      assertThat(kit.context().config().get("destino")).contains("pasta");
    }
  }

  private static final class FakePlugin implements AutomationPlugin {

    @Override
    public PluginMetadata metadata() {
      return new PluginMetadata("fake-plugin", "Fake", "1.0.0", "1.0");
    }

    @Override
    public List<Automation> automations() {
      return List.of(
          new Automation() {
            @Override
            public AutomationDescriptor describe() {
              return AutomationDescriptor.of("fake.eco", "Eco", "devolve o texto");
            }

            @Override
            public AutomationResult execute(AutomationContext context) {
              return AutomationResult.success(
                  Map.of("eco", context.params().string("texto").orElse("")), "ok");
            }
          },
          new Automation() {
            @Override
            public AutomationDescriptor describe() {
              return AutomationDescriptor.of("fake.grava", "Grava", "usa storage");
            }

            @Override
            public AutomationResult execute(AutomationContext context) {
              context.storage().put("ultimo", "1");
              return AutomationResult.success();
            }
          },
          new Automation() {
            @Override
            public AutomationDescriptor describe() {
              return AutomationDescriptor.of(
                  "fake.loga",
                  "Loga",
                  "loga parametro sensivel",
                  List.of(
                      ParamSpec.optional("segredo", ParamType.STRING, "segredo").asSensitive()));
            }

            @Override
            public AutomationResult execute(AutomationContext context) {
              context.log().info("usando " + context.params().string("segredo").orElse(""));
              return AutomationResult.success();
            }
          });
    }

    @Override
    public void initialize(PluginContext context) {}

    @Override
    public void shutdown() {}
  }
}
