package com.accord.sia.api;

import java.util.List;
import java.util.Optional;

/**
 * Armazenamento chave-valor namespaced por plugin.
 *
 * <p>Toda operacao e executada no namespace do plugin dono do contexto: um plugin nao enxerga nem
 * alcanca a chave de outro, e o Core rejeita chave com caminho.
 */
public interface Storage {

  Optional<String> get(String key);

  void put(String key, String value);

  void delete(String key);

  /** Chaves existentes que comecam com o prefixo informado (lista vazia se nao houver). */
  List<String> keys(String prefix);
}
