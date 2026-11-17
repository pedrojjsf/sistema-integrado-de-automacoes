package ${package};

import com.accord.sia.api.Automation;
import com.accord.sia.api.AutomationContext;
import com.accord.sia.api.AutomationDescriptor;
import com.accord.sia.api.AutomationResult;
import com.accord.sia.api.ParamSpec;
import com.accord.sia.api.ParamType;
import java.util.List;
import java.util.Map;

/** Automacao de exemplo: devolve o texto recebido. */
public final class SampleAutomation implements Automation {

  @Override
  public AutomationDescriptor describe() {
    return AutomationDescriptor.of(
        "${pluginId}.echo",
        "Echo",
        "Devolve o texto informado",
        List.of(ParamSpec.required("texto", ParamType.STRING, "texto a devolver")));
  }

  @Override
  public AutomationResult execute(AutomationContext context) {
    String texto = context.params().string("texto").orElse("");
    context.log().info("executando echo");
    return AutomationResult.success(Map.of("eco", texto), "ok");
  }
}
