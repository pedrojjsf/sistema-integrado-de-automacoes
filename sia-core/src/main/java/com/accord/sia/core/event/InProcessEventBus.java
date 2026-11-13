package com.accord.sia.core.event;

import com.accord.sia.api.EventPublisher;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Barramento de eventos em memoria.
 *
 * <p>Entrega sincrona, sem persistencia: payload de evento nao e historico. Quem precisa de trilha
 * auditavel grava em {@code run}/{@code run_log}.
 */
public final class InProcessEventBus implements EventPublisher {

  private final Map<String, List<Consumer<Map<String, Object>>>> subscribers =
      new ConcurrentHashMap<>();

  @Override
  public void publish(String type, Map<String, Object> payload) {
    for (Consumer<Map<String, Object>> subscriber : subscribers.getOrDefault(type, List.of())) {
      subscriber.accept(payload == null ? Map.of() : Map.copyOf(payload));
    }
  }

  public void subscribe(String type, Consumer<Map<String, Object>> handler) {
    subscribers.computeIfAbsent(type, key -> new CopyOnWriteArrayList<>()).add(handler);
  }
}
