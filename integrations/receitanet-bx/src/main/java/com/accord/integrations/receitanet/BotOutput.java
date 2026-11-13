package com.accord.integrations.receitanet;

import com.accord.sia.api.AutomationContext;
import com.accord.sia.api.AutomationResult;
import com.accord.sia.api.ProcessResult;
import java.util.List;
import java.util.Map;

/**
 * Traduz o resultado do bot em {@link AutomationResult}.
 *
 * <p>O codigo de saida do launcher do jpackage nao e confiavel sozinho (o {@code diag} retorna
 * {@code -1} mesmo funcionando), entao a decisao combina exit code e conteudo. Os codigos do Derby
 * tem traducao propria porque a mensagem crua nao ajuda quem esta operando.
 */
final class BotOutput {

  private static final int MESSAGE_LIMIT = 500;
  private static final Map<String, String> KNOWN_ERRORS =
      Map.of(
          "XSDB6", "BOT_BANCO_OCUPADO",
          "42818", "BOT_BANCO_INCOMPATIVEL",
          "42X05", "BOT_BANCO_INCOMPATIVEL",
          "42S02", "BOT_BANCO_INCOMPATIVEL",
          "XJ040", "BOT_BANCO_INACESSIVEL");

  private BotOutput() {}

  static AutomationResult interpret(ProcessResult result, AutomationContext context) {
    if (result.timedOut()) {
      return AutomationResult.failure(
          "BOT_TIMEOUT", "o bot excedeu o tempo limite e foi encerrado");
    }
    if (result.succeeded()) {
      return AutomationResult.success(outputs(result), lastLine(result.stdout()));
    }
    String errorCode = classify(result);
    String detail = detail(result);
    context.log().warn("bot falhou (" + errorCode + "): " + detail);
    return AutomationResult.failure(errorCode, detail);
  }

  private static Map<String, Object> outputs(ProcessResult result) {
    return Map.of("exitCode", result.exitCode(), "linhas", countLines(result.stdout()));
  }

  static String classify(ProcessResult result) {
    String combined = (result.stdout() + "\n" + result.stderr()).toUpperCase();
    for (Map.Entry<String, String> entry : KNOWN_ERRORS.entrySet()) {
      if (combined.contains(entry.getKey())) {
        return entry.getValue();
      }
    }
    return "BOT_FALHOU";
  }

  static String detail(ProcessResult result) {
    String text = result.stderr().isBlank() ? result.stdout() : result.stderr();
    String trimmed = trim(text);
    if (trimmed.isBlank()) {
      return "o bot terminou com codigo " + result.exitCode() + " sem mensagem";
    }
    return trimmed;
  }

  static String lastLine(String text) {
    List<String> lines = text.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
    return lines.isEmpty() ? "" : trim(lines.get(lines.size() - 1));
  }

  private static int countLines(String text) {
    return (int) text.lines().filter(line -> !line.isBlank()).count();
  }

  private static String trim(String text) {
    String cleaned = text == null ? "" : text.strip();
    return cleaned.length() <= MESSAGE_LIMIT ? cleaned : cleaned.substring(0, MESSAGE_LIMIT);
  }
}
