package testplugin.alpha;

import com.accord.sia.api.Automation;
import com.accord.sia.api.AutomationContext;
import com.accord.sia.api.AutomationDescriptor;
import com.accord.sia.api.AutomationResult;

public final class AlphaAutomation implements Automation {

  @Override
  public AutomationDescriptor describe() {
    return AutomationDescriptor.of("alpha.run", "Alpha run", "descricao");
  }

  @Override
  public AutomationResult execute(AutomationContext context) {
    return AutomationResult.success();
  }
}
