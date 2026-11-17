package com.accord.sia.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Autenticador de desenvolvimento: tokens estaticos com identidade e papeis fixos.
 *
 * <p>Existe para destravar o time antes do IdP. Recusa ser criado com {@code env=prod} e compara
 * tokens em tempo constante para nao vazar prefixo por tempo de resposta.
 */
public final class DevTokens implements Authenticator {

  private final Map<String, Principal> principals;

  private DevTokens(Map<String, Principal> principals) {
    this.principals = principals;
  }

  public static DevTokens forEnvironment(String env, Map<String, Principal> tokenToPrincipal) {
    String normalizedEnv = env == null ? "" : env.toLowerCase(Locale.ROOT);
    if ("prod".equals(normalizedEnv) || "production".equals(normalizedEnv)) {
      throw new IllegalStateException("auth.mode=dev e proibido em producao");
    }
    if (tokenToPrincipal == null || tokenToPrincipal.isEmpty()) {
      throw new IllegalArgumentException("auth.mode=dev exige ao menos um token configurado");
    }
    return new DevTokens(Map.copyOf(tokenToPrincipal));
  }

  @Override
  public Optional<Principal> authenticate(String bearerToken) {
    if (bearerToken == null || bearerToken.isBlank()) {
      return Optional.empty();
    }
    Principal found = null;
    for (Map.Entry<String, Principal> entry : principals.entrySet()) {
      if (constantTimeEquals(entry.getKey(), bearerToken)) {
        found = entry.getValue();
      }
    }
    return Optional.ofNullable(found);
  }

  private static boolean constantTimeEquals(String expected, String provided) {
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
  }
}
