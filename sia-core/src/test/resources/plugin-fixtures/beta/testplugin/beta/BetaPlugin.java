package testplugin.beta;

import com.accord.sia.api.Automation;
import com.accord.sia.api.AutomationPlugin;
import com.accord.sia.api.PluginContext;
import com.accord.sia.api.PluginMetadata;
import java.util.List;

public final class BetaPlugin implements AutomationPlugin {

  @Override
  public PluginMetadata metadata() {
    return new PluginMetadata("beta-plugin", "Beta", "1.0.0", "1.0");
  }

  @Override
  public List<Automation> automations() {
    return List.of(new BetaAutomation());
  }

  @Override
  public void initialize(PluginContext context) {}

  @Override
  public void shutdown() {}
}
