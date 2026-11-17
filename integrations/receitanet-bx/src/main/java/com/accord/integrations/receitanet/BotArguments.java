package com.accord.integrations.receitanet;

import com.accord.sia.api.AutomationException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;

/**
 * Validacao de entrada e montagem de argumentos do bot.
 *
 * <p>Valida antes de subir processo: data inexistente (31/04) ou arquivo ausente falham em
 * milissegundos, em vez de gastar uma execucao do bot para descobrir.
 */
final class BotArguments {

  static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

  private BotArguments() {}

  static LocalDate date(String value, String field) throws AutomationException {
    try {
      return LocalDate.parse(value, DATE);
    } catch (DateTimeParseException error) {
      throw new AutomationException(
          "PARAMETRO_INVALIDO", field + ": '" + value + "' nao e uma data valida (dd/MM/aaaa)");
    }
  }

  static List<String> systemIds(String value) throws AutomationException {
    List<String> ids = new ArrayList<>();
    for (String part : value.split(",")) {
      String id = part.trim();
      if (id.isEmpty()) {
        continue;
      }
      if (!id.chars().allMatch(Character::isDigit)) {
        throw new AutomationException(
            "PARAMETRO_INVALIDO", "sistemas: '" + id + "' nao e um id numerico");
      }
      ids.add(id);
    }
    if (ids.isEmpty()) {
      throw new AutomationException("PARAMETRO_INVALIDO", "sistemas: informe ao menos um id");
    }
    return ids;
  }

  static Path readableFile(String value) throws AutomationException {
    Path path = Path.of(value);
    if (!Files.isRegularFile(path)) {
      throw new AutomationException("PARAMETRO_INVALIDO", "arquivo nao encontrado: " + value);
    }
    return path;
  }

  static String requireText(String value, String field) throws AutomationException {
    if (value == null || value.isBlank()) {
      throw new AutomationException("PARAMETRO_INVALIDO", field + " e obrigatorio");
    }
    return value.trim();
  }
}
