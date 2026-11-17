package com.accord.sia.auth;

import java.util.Objects;
import java.util.Set;

/**
 * Identidade autenticada: quem agiu e com quais papeis.
 *
 * @param subject identificador estavel (claim {@code sub} do IdP, ou token de dev)
 * @param displayName nome legivel para o historico de execucao
 * @param roles papeis concedidos; vazio significa "autenticado sem poder"
 * @param origin diferencia pessoa de disparo automatico (scheduler, workflow)
 */
public record Principal(String subject, String displayName, Set<SiaRole> roles, Origin origin) {

  public enum Origin {
    USER,
    SYSTEM
  }

  public Principal {
    Objects.requireNonNull(subject, "subject");
    if (subject.isBlank()) {
      throw new IllegalArgumentException("subject nao pode ser vazio");
    }
    roles = roles == null ? Set.of() : Set.copyOf(roles);
    origin = origin == null ? Origin.USER : origin;
  }

  public static Principal user(String subject, String displayName, Set<SiaRole> roles) {
    return new Principal(subject, displayName, roles, Origin.USER);
  }

  /** Identidade de execucao automatica: sem usuario logado, mas com poder total. */
  public static Principal system(String name) {
    return new Principal("system:" + name, name, Set.of(SiaRole.ADMIN), Origin.SYSTEM);
  }

  public boolean can(Permission permission) {
    return permission != null && roles.stream().anyMatch(role -> role.implies(permission));
  }
}
