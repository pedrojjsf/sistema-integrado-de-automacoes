package testplugin.panel;

import com.accord.sia.api.Automation;
import com.accord.sia.api.AutomationContext;
import com.accord.sia.api.AutomationDescriptor;
import com.accord.sia.api.AutomationPlugin;
import com.accord.sia.api.AutomationResult;
import com.accord.sia.api.PluginContext;
import com.accord.sia.api.PluginMetadata;
import java.util.List;
import java.util.Map;

/** Fixture com interface propria: prova que o Core serve os arquivos de ui/. */
public final class PanelPlugin implements AutomationPlugin {

  @Override
  public PluginMetadata metadata() {
    return new PluginMetadata("panel-plugin", "Panel", "1.0.0", "1.0", "Testes");
  }

  @Override
  public List<Automation> automations() {
    return List.of(
        new Automation() {
          @Override
          public AutomationDescriptor describe() {
            return AutomationDescriptor.of("panel.run", "Rodar", "executa pelo painel");
          }

          @Override
          public AutomationResult execute(AutomationContext context) {
            return AutomationResult.success(Map.of("ok", "true"), "feito");
          }
        });
  }

  @Override
  public void initialize(PluginContext context) {
    // nada a inicializar
  }

  @Override
  public void shutdown() {
    // sem recursos
  }
}
