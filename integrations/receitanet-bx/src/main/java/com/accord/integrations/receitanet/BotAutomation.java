package com.accord.integrations.receitanet;

import com.accord.sia.api.Automation;
import com.accord.sia.api.AutomationContext;
import com.accord.sia.api.AutomationDescriptor;
import com.accord.sia.api.AutomationException;
import com.accord.sia.api.AutomationResult;
import com.accord.sia.api.Parameters;
import com.accord.sia.api.ProcessRequest;
import com.accord.sia.api.ProcessResult;
import java.time.Duration;
import java.util.List;

/**
 * Base das automacoes do bot: monta argumentos, roda pela allowlist e interpreta a saida.
 *
 * <p>O plugin nunca chama {@code ProcessBuilder}. Ele tambem nao sabe onde o bot esta: pede o nome
 * logico {@code receitanet-bx} ao Core, que resolve o caminho pela configuracao.
 */
abstract class BotAutomation implements Automation {

  static final String EXECUTABLE = "receitanet-bx";

  private final AutomationDescriptor descriptor;
  private final Duration defaultTimeout;

  protected BotAutomation(AutomationDescriptor descriptor, Duration defaultTimeout) {
    this.descriptor = descriptor;
    this.defaultTimeout = defaultTimeout;
  }

  @Override
  public final AutomationDescriptor describe() {
    return descriptor;
  }

  @Override
  public final AutomationResult execute(AutomationContext context) throws AutomationException {
    List<String> arguments = arguments(context.params());
    ProcessRequest request = new ProcessRequest(EXECUTABLE, arguments, timeout(), List.of());
    ProcessResult result = context.processes().run(request);
    return interpret(result, context);
  }

  /** Argumentos do bot, ja validados. */
  protected abstract List<String> arguments(Parameters params) throws AutomationException;

  /** Traducao da saida; sobrescrito quando o exit code do bot nao e confiavel. */
  protected AutomationResult interpret(ProcessResult result, AutomationContext context) {
    return BotOutput.interpret(result, context);
  }

  protected Duration timeout() {
    return defaultTimeout;
  }
}
