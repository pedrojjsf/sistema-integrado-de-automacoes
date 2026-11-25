package com.accord.sia.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class PluginContractTest {

  private static final PluginMetadata GOOD_METADATA =
      new PluginMetadata("demo-plugin", "Demo Plugin", "1.0.0", "1.0", "Accord");

  private static Automation automation(AutomationDescriptor descriptor) {
    return new Automation() {
      @Override
      public AutomationDescriptor describe() {
        return descriptor;
      }

      @Override
      public AutomationResult execute(AutomationContext context) {
        return AutomationResult.success();
      }
    };
  }

  private static AutomationPlugin pluginOf(PluginMetadata metadata, List<Automation> automations) {
    return new AutomationPlugin() {
      @Override
      public PluginMetadata metadata() {
        return metadata;
      }

      @Override
      public List<Automation> automations() {
        return automations;
      }

      @Override
      public void initialize(PluginContext context) {}

      @Override
      public void shutdown() {}
    };
  }

  private static AutomationDescriptor lote() {
    return AutomationDescriptor.of(
        "demo.lote",
        "Lote por CNPJ",
        "Cria e baixa pedidos por CNPJ",
        List.of(
            ParamSpec.required("arquivo", ParamType.STRING, "lista de CNPJs"),
            ParamSpec.optional("pin", ParamType.STRING, "PIN do certificado").asSensitive()));
  }

  @Test
  void aceitaPluginValido() throws InvalidPluginException {
    PluginContract.validate(pluginOf(GOOD_METADATA, List.of(automation(lote()))));
  }

  @Test
  void rejeitaMetadataNulo() {
    assertThatThrownBy(() -> PluginContract.validate(pluginOf(null, List.of(automation(lote())))))
        .isInstanceOfSatisfying(
            InvalidPluginException.class, error -> assertThat(error.field()).isEqualTo("metadata"));
  }

  @Test
  void rejeitaIdDePluginComMaiuscula() {
    var metadata = new PluginMetadata("Demo-Plugin", "Demo Plugin", "1.0.0", "1.0");

    assertThatThrownBy(() -> PluginContract.validate(metadata))
        .isInstanceOfSatisfying(
            InvalidPluginException.class,
            error -> assertThat(error.field()).isEqualTo("metadata.id"));
  }

  @Test
  void rejeitaVersaoForaDeSemver() {
    var metadata = new PluginMetadata("demo-plugin", "Demo Plugin", "1.0", "1.0");

    assertThatThrownBy(() -> PluginContract.validate(metadata))
        .isInstanceOfSatisfying(
            InvalidPluginException.class,
            error -> assertThat(error.field()).isEqualTo("metadata.version"));
  }

  @Test
  void rejeitaApiVersionIncompativel() {
    var metadata = new PluginMetadata("demo-plugin", "Demo Plugin", "1.0.0", "2.0");

    assertThatThrownBy(() -> PluginContract.validate(metadata))
        .isInstanceOfSatisfying(
            InvalidPluginException.class,
            error -> assertThat(error.field()).isEqualTo("metadata.apiVersion"));
  }

  @Test
  void rejeitaPluginSemAutomacao() {
    assertThatThrownBy(() -> PluginContract.validateAutomations(List.of()))
        .isInstanceOfSatisfying(
            InvalidPluginException.class,
            error -> assertThat(error.field()).isEqualTo("automations"));
  }

  @Test
  void rejeitaAutomacaoDuplicada() {
    assertThatThrownBy(() ->
            PluginContract.validateAutomations(List.of(automation(lote()), automation(lote()))))
        .isInstanceOfSatisfying(
            InvalidPluginException.class,
            error -> assertThat(error.field()).contains("demo.lote"));
  }

  @Test
  void rejeitaIdDeAutomacaoSemFormatoPluginAcao() {
    var descriptor = AutomationDescriptor.of("lote", "Lote", "sem prefixo");

    assertThatThrownBy(() -> PluginContract.validate(descriptor))
        .isInstanceOfSatisfying(
            InvalidPluginException.class,
            error -> assertThat(error.field()).isEqualTo("automation.id"));
  }

  @Test
  void rejeitaParametroSensivelComValorPadrao() {
    var spec = ParamSpec.optional("token", ParamType.STRING, "token", "abc").asSensitive();
    var descriptor = AutomationDescriptor.of("demo.lote", "Lote", "desc", List.of(spec));

    assertThatThrownBy(() -> PluginContract.validate(descriptor))
        .isInstanceOfSatisfying(
            InvalidPluginException.class, error -> assertThat(error.field()).contains("token"));
  }

  @Test
  void rejeitaParametroDuplicado() {
    var descriptor =
        AutomationDescriptor.of(
            "demo.lote",
            "Lote",
            "desc",
            List.of(
                ParamSpec.required("arquivo", ParamType.STRING, "a"),
                ParamSpec.required("arquivo", ParamType.STRING, "b")));

    assertThatThrownBy(() -> PluginContract.validate(descriptor))
        .isInstanceOfSatisfying(
            InvalidPluginException.class,
            error -> assertThat(error.field()).contains("demo.lote"));
  }

  @Test
  void rejeitaAutomacaoNulaNaLista() {
    assertThatThrownBy(
            () -> PluginContract.validateAutomations(java.util.Arrays.asList((Automation) null)))
        .isInstanceOfSatisfying(
            InvalidPluginException.class,
            error -> assertThat(error.field()).isEqualTo("automations"));
  }

  @Test
  void compatibilidadeConsideraApenasMajor() {
    assertThat(PluginContract.isApiCompatible("1.0")).isTrue();
    assertThat(PluginContract.isApiCompatible("1.7")).isTrue();
    assertThat(PluginContract.isApiCompatible("2.0")).isFalse();
    assertThat(PluginContract.isApiCompatible(null)).isFalse();
  }
}
