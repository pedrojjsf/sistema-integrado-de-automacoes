package com.accord.integrations.receitanet;

import static org.assertj.core.api.Assertions.assertThat;

import com.accord.sia.api.Automation;
import com.accord.sia.api.AutomationDescriptor;
import com.accord.sia.api.InvalidPluginException;
import com.accord.sia.api.ParamType;
import com.accord.sia.api.PluginContract;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReceitanetPluginTest {

  private final ReceitanetPlugin plugin = new ReceitanetPlugin();

  @Test
  void cumpreOContratoDaApi() throws InvalidPluginException {
    PluginContract.validate(plugin);
  }

  @Test
  void expoeAsSeisAutomacoes() {
    assertThat(plugin.automations())
        .extracting(Automation::id)
        .containsExactly(
            "receitanet.diag",
            "receitanet.catalogo",
            "receitanet.lote",
            "receitanet.pedidos",
            "receitanet.pendentes",
            "receitanet.baixar");
  }

  @Test
  void loteExigeOsQuatroParametrosObrigatorios() {
    AutomationDescriptor lote = descriptor("receitanet.lote");

    assertThat(lote.params())
        .filteredOn(spec -> spec.required())
        .extracting(spec -> spec.name())
        .containsExactlyInAnyOrder("arquivo", "sistemas", "inicio", "fim");
    assertThat(lote.params())
        .filteredOn(spec -> spec.name().equals("baixar"))
        .singleElement()
        .satisfies(spec -> assertThat(spec.type()).isEqualTo(ParamType.BOOLEAN));
  }

  @Test
  void baixarAceitaPedidoOuTodos() {
    AutomationDescriptor baixar = descriptor("receitanet.baixar");

    assertThat(baixar.params())
        .extracting(spec -> spec.name())
        .containsExactlyInAnyOrder("pedido", "todos", "timeoutSegundos");
  }

  @Test
  void nenhumParametroEhSensivelPorqueNaoHaPin() {
    List<Automation> automations = plugin.automations();

    assertThat(automations)
        .flatExtracting(automation -> automation.describe().params())
        .noneMatch(spec -> spec.sensitive());
  }

  @Test
  void metadadosBatemComOManifesto() {
    assertThat(plugin.metadata().id()).isEqualTo("receitanet-bx");
    assertThat(plugin.metadata().apiVersion()).isEqualTo("1.0");
  }

  private AutomationDescriptor descriptor(String automationId) {
    return plugin.automations().stream()
        .filter(automation -> automation.id().equals(automationId))
        .findFirst()
        .orElseThrow()
        .describe();
  }
}
