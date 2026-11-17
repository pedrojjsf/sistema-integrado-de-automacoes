package com.accord.sia.api;

/**
 * Quem disparou a execucao.
 *
 * @param subject identificador estavel (claim {@code sub} do IdP, ou {@code system:scheduler})
 * @param displayName nome legivel para o historico
 * @param onBehalfOf para disparo automatico, o {@code sub} de quem criou a agenda; nulo quando ha
 *     usuario na origem
 */
public record Actor(String subject, String displayName, String onBehalfOf) {

  public Actor {
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("subject nao pode ser vazio");
    }
  }

  public static Actor user(String subject, String displayName) {
    return new Actor(subject, displayName, null);
  }

  public static Actor system(String name, String onBehalfOf) {
    return new Actor("system:" + name, name, onBehalfOf);
  }

  public boolean isSystem() {
    return subject.startsWith("system:");
  }
}
