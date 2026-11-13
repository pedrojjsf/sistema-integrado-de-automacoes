package com.accord.sia.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class PluginContractTest {

  private static final PluginMetadata GOOD_METADATA =
      new PluginMetadata("receitanet-bx", "Receitanet BX", "1.0.0", "1.0", "Accord");

  private static AutomationPlugin pluginOf(
      PluginMetadata metadata, List<AutomationDescriptor> descriptors) {
    return new AutomationPlugin() {
      @Override
      public PluginMetadata metadata() {
        return metadata;
      }

      @Override
      public List<AutomationDescriptor> automations() {
        return descriptors;
      }

      @Override
      public void initialize(PluginContext context) {}

      @Override
      public void shutdown() {}
    };
  }

  private static AutomationDescriptor loteCnpj() {
    return AutomationDescriptor.of(
        "receitanet.lote",
        "Lote por CNPJ",
        "Cria e baixa pedidos por CNPJ",
        List.of(
            ParamSpec.required("arquivo", ParamType.STRING, "lista de CNPJs"),
            ParamSpec.optional("pin", ParamType.STRING, "PIN do certificado").asSensitive()));
  }

  @Test
  void aceitaPluginValido() throws InvalidPluginException {
    PluginContract.validate(pluginOf(GOOD_METADATA, List.of(loteCnpj())));
  }

  @Test
  void rejeitaMetadataNulo() {
    assertThatThrownBy(() -> PluginContract.validate(pluginOf(null, List.of(loteCnpj()))))
        .isInstanceOfSatisfying(
            InvalidPluginException.class, error -> assertThat(error.field()).isEqualTo("metadata"));
  }

  @Test
  void rejeitaIdDePluginComMaiuscula() {
    var metadata = new PluginMetadata("Receitanet-BX", "Receitanet BX", "1.0.0", "1.0");

    assertThatThrownBy(() -> PluginContract.validate(metadata))
        .isInstanceOfSatisfying(
            InvalidPluginException.class,
            error -> assertThat(error.field()).isEqualTo("metadata.id"));
  }

  @Test
  void rejeitaVersaoForaDeSemver() {
    var metadata = new PluginMetadata("receitanet-bx", "Receitanet BX", "1.0", "1.0");

    assertThatThrownBy(() -> PluginContract.validate(metadata))
        .isInstanceOfSatisfying(
            InvalidPluginException.class,
            error -> assertThat(error.field()).isEqualTo("metadata.version"));
  }

  @Test
  void rejeitaApiVersionIncompativel() {
    var metadata = new PluginMetadata("receitanet-bx", "Receitanet BX", "1.0.0", "2.0");

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
    assertThatThrownBy(() -> PluginContract.validateAutomations(List.of(loteCnpj(), loteCnpj())))
        .isInstanceOfSatisfying(
            InvalidPluginException.class,
            error -> assertThat(error.field()).contains("receitanet.lote"));
  }

  @Test
  void rejeitaIdDeAutomacaoSemFormatoPluginAcao() {
    var descriptor = AutomationDescriptor.of("send", "Lote por CNPJ", "sem prefixo");

    assertThatThrownBy(() -> PluginContract.validate(descriptor))
        .isInstanceOfSatisfying(
            InvalidPluginException.class,
            error -> assertThat(error.field()).isEqualTo("automation.id"));
  }

  @Test
  void rejeitaParametroSensivelComValorPadrao() {
    var spec = ParamSpec.optional("token", ParamType.STRING, "token", "abc").asSensitive();
    var descriptor = AutomationDescriptor.of("receitanet.lote", "Lote por CNPJ", "desc", List.of(spec));

    assertThatThrownBy(() -> PluginContract.validate(descriptor))
        .isInstanceOfSatisfying(
            InvalidPluginException.class, error -> assertThat(error.field()).contains("token"));
  }

  @Test
  void rejeitaParametroDuplicado() {
    var descriptor =
        AutomationDescriptor.of(
            "receitanet.lote",
            "Lote por CNPJ",
            "desc",
            List.of(
                ParamSpec.required("to", ParamType.STRING, "a"),
                ParamSpec.required("to", ParamType.STRING, "b")));

    assertThatThrownBy(() -> PluginContract.validate(descriptor))
        .isInstanceOfSatisfying(
            InvalidPluginException.class,
            error -> assertThat(error.field()).contains("receitanet.lote"));
  }

  @Test
  void compatibilidadeConsideraApenasMajor() {
    assertThat(PluginContract.isApiCompatible("1.0")).isTrue();
    assertThat(PluginContract.isApiCompatible("1.7")).isTrue();
    assertThat(PluginContract.isApiCompatible("2.0")).isFalse();
    assertThat(PluginContract.isApiCompatible(null)).isFalse();
  }
}
