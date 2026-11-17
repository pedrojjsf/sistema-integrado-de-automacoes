package ${package};

import com.accord.sia.api.Automation;
import com.accord.sia.api.AutomationPlugin;
import com.accord.sia.api.PluginContext;
import com.accord.sia.api.PluginMetadata;
import java.util.List;

/**
 * Plugin gerado pelo arquétipo do SIA.
 *
 * <p>Implemente suas automacoes em classes {@link Automation} e registre-as em {@link #automations()}.
 */
public final class SamplePlugin implements AutomationPlugin {

  private PluginContext context;

  @Override
  public PluginMetadata metadata() {
    return new PluginMetadata("${pluginId}", "${pluginId}", "1.0.0", "1.0", "${groupId}");
  }

  @Override
  public List<Automation> automations() {
    return List.of(new SampleAutomation());
  }

  @Override
  public void initialize(PluginContext context) {
    this.context = context;
    context.log().info("plugin ${pluginId} inicializado");
  }

  @Override
  public void shutdown() {
    // libere recursos aqui; precisa ser idempotente
  }

  PluginContext context() {
    return context;
  }
}
