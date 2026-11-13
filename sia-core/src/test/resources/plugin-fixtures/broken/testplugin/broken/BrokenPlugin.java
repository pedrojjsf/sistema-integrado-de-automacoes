package testplugin.broken;

import com.accord.sia.api.AutomationDescriptor;
import com.accord.sia.api.AutomationPlugin;
import com.accord.sia.api.PluginContext;
import com.accord.sia.api.PluginMetadata;
import java.util.List;

/** Viola o contrato: plugin sem nenhuma automacao. */
public final class BrokenPlugin implements AutomationPlugin {

  @Override
  public PluginMetadata metadata() {
    return new PluginMetadata("broken-plugin", "Broken", "1.0.0", "1.0");
  }

  @Override
  public List<AutomationDescriptor> automations() {
    return List.of();
  }

  @Override
  public void initialize(PluginContext context) {}

  @Override
  public void shutdown() {}
}
