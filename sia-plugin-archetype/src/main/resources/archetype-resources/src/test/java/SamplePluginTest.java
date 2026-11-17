package ${package};

import static org.assertj.core.api.Assertions.assertThat;

import com.accord.sia.testkit.Permissions;
import com.accord.sia.testkit.PluginTestKit;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SamplePluginTest {

  @Test
  void executaEcho() {
    try (PluginTestKit kit = PluginTestKit.start(new SamplePlugin(), Permissions.all())) {
      var result = kit.run("${pluginId}.echo", Map.of("texto", "ola"));

      result.assertSuccess();
      assertThat(result.outputs()).containsEntry("eco", "ola");
    }
  }
}
