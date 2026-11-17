package com.accord.sia.core.run;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Barramento de eventos de execucao, consumido por SSE. Nao persiste nada. */
public final class RunEventBus {

  private final Map<String, List<Consumer<RunEvent>>> listeners = new ConcurrentHashMap<>();

  public AutoCloseable subscribe(String runId, Consumer<RunEvent> listener) {
    List<Consumer<RunEvent>> bucket =
        listeners.computeIfAbsent(runId, key -> new CopyOnWriteArrayList<>());
    bucket.add(listener);
    return () -> {
      bucket.remove(listener);
      if (bucket.isEmpty()) {
        listeners.remove(runId, bucket);
      }
    };
  }

  public void publish(RunEvent event) {
    for (Consumer<RunEvent> listener : listeners.getOrDefault(event.runId(), List.of())) {
      try {
        listener.accept(event);
      } catch (RuntimeException ignored) {
        // cliente desconectado nao pode quebrar a execucao
      }
    }
  }
}
