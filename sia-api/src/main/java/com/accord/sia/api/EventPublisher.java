package com.accord.sia.api;

import java.util.Map;

/**
 * Publicacao de eventos para o Core.
 *
 * <p>O payload serve para coordenacao em memoria e para gatilhos de workflow. Ele <b>nao</b> e
 * persistido: o historico guarda tipo e chaves, nunca conteudo.
 */
public interface EventPublisher {

  void publish(String type, Map<String, Object> payload);
}
