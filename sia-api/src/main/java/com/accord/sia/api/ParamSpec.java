package com.accord.sia.api;

import java.util.Objects;

/**
 * Declaracao de um parametro aceito por uma automacao.
 *
 * <p>{@code sensitive} e o ponto central da regra "nada sensivel no banco nem no log": o Core usa
 * essa marca para redigir o valor antes de persistir qualquer coisa e para impedir que ele apareca
 * em log ou no historico.
 *
 * @param name identificador em camelCase (ex.: {@code smtpPassword})
 * @param type tipo esperado
 * @param required se o parametro precisa estar presente na chamada
 * @param sensitive se o valor nunca pode ser persistido nem logado
 * @param description texto curto para a documentacao e a API
 * @param defaultValue valor padrao; proibido quando {@code sensitive}
 */
public record ParamSpec(
    String name,
    ParamType type,
    boolean required,
    boolean sensitive,
    String description,
    String defaultValue) {

  public ParamSpec {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
  }

  public static ParamSpec required(String name, ParamType type, String description) {
    return new ParamSpec(name, type, true, false, description, null);
  }

  public static ParamSpec optional(String name, ParamType type, String description) {
    return new ParamSpec(name, type, false, false, description, null);
  }

  public static ParamSpec optional(
      String name, ParamType type, String description, String defaultValue) {
    return new ParamSpec(name, type, false, false, description, defaultValue);
  }

  /** Mesmo parametro, marcado como sensivel. */
  public ParamSpec asSensitive() {
    return new ParamSpec(name, type, required, true, description, defaultValue);
  }

  public ParamSpec describedAs(String newDescription) {
    return new ParamSpec(name, type, required, sensitive, newDescription, defaultValue);
  }
}
