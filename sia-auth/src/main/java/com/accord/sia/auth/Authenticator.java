package com.accord.sia.auth;

import java.util.Optional;

/**
 * Resolve um bearer token em identidade.
 *
 * <p>Duas implementacoes previstas: {@link DevTokens} (tokens estaticos locais) e o resource server
 * OIDC que valida JWT contra o JWKS do IdP. A autorizacao nao depende de qual esta ativa.
 */
@FunctionalInterface
public interface Authenticator {

  Optional<Principal> authenticate(String bearerToken);
}
