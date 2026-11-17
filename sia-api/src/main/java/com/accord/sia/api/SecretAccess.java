package com.accord.sia.api;

import java.util.Optional;

/**
 * Leitura de segredos, escopada ao plugin.
 *
 * <p>O nome e resolvido dentro do namespace do plugin; pedir segredo que nao pertence a ele resulta
 * em {@link PermissionDeniedException}, nao em valor nulo silencioso.
 */
public interface SecretAccess {

  Optional<String> get(String name);
}
