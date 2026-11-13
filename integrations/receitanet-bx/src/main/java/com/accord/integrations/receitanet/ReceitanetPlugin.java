package com.accord.integrations.receitanet;

import com.accord.sia.api.Automation;
import com.accord.sia.api.AutomationPlugin;
import com.accord.sia.api.PluginContext;
import com.accord.sia.api.PluginMetadata;
import java.util.List;
import java.util.Locale;

/**
 * Plugin do Receitanet BX.
 *
 * <p>Adaptador fino sobre o CLI headless do bot (ADR-0001). O bot continua sendo processo separado:
 * um crash do motor oficial nao derruba o daemon, e o banco Derby fica isolado.
 */
public final class ReceitanetPlugin implements AutomationPlugin {

  @Override
  public PluginMetadata metadata() {
    return new PluginMetadata("receitanet-bx", "Receitanet BX", "1.0.0", "1.0", "Accord");
  }

  @Override
  public List<Automation> automations() {
    return ReceitanetAutomations.all();
  }

  @Override
  public void initialize(PluginContext context) {
    context.log().info("plugin receitanet-bx inicializado");
    if (!isWindows()) {
      throw new IllegalStateException(
          "receitanet-bx exige Windows (certificado ICP-Brasil no repositorio do sistema)");
    }
  }

  @Override
  public void shutdown() {
    // nao ha recurso proprio: o processo do bot e encerrado pelo Core
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }
}
