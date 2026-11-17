package testplugin.beta;

import com.accord.sia.api.Automation;
import com.accord.sia.api.AutomationContext;
import com.accord.sia.api.AutomationDescriptor;
import com.accord.sia.api.AutomationResult;

public final class BetaAutomation implements Automation {

  @Override
  public AutomationDescriptor describe() {
    return AutomationDescriptor.of("beta.run", "Beta run", "descricao");
  }

  @Override
  public AutomationResult execute(AutomationContext context) {
    return AutomationResult.success();
  }
}
