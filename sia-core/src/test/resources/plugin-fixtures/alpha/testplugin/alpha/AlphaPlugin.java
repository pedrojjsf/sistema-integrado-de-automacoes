package testplugin.alpha;

import com.accord.sia.api.AutomationDescriptor;
import com.accord.sia.api.AutomationPlugin;
import com.accord.sia.api.PluginContext;
import com.accord.sia.api.PluginMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Plugin de teste que nao depende de nada alem de sia-api. */
public final class AlphaPlugin implements AutomationPlugin {

  @Override
  public PluginMetadata metadata() {
    return new PluginMetadata("alpha-plugin", "Alpha", "1.0.0", "1.0");
  }

  @Override
  public List<AutomationDescriptor> automations() {
    return List.of(AutomationDescriptor.of("alpha.run", "Alpha run", "descricao"));
  }

  @Override
  public void initialize(PluginContext context) {}

  @Override
  public void shutdown() {
    String marker = System.getProperty("sia.test.shutdownMarker");
    if (marker == null) {
      return;
    }
    try {
      Files.writeString(Path.of(marker), "shutdown");
    } catch (IOException ignored) {
      // teste de melhor esforco
    }
  }
}
