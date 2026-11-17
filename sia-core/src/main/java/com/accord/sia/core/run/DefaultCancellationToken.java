package com.accord.sia.core.run;

import com.accord.sia.api.AutomationCancelledException;
import com.accord.sia.api.CancellationToken;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Token de cancelamento com notificacao de listeners. */
public final class DefaultCancellationToken implements CancellationToken {

  private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
  private volatile boolean cancelled;

  public void cancel() {
    if (cancelled) {
      return;
    }
    cancelled = true;
    for (Runnable listener : listeners) {
      try {
        listener.run();
      } catch (RuntimeException ignored) {
        // um listener problematico nao impede os demais de reagir
      }
    }
  }

  @Override
  public boolean isCancelled() {
    return cancelled;
  }

  @Override
  public void throwIfCancelled() {
    if (cancelled) {
      throw new AutomationCancelledException();
    }
  }

  @Override
  public void onCancel(Runnable listener) {
    if (listener == null) {
      return;
    }
    if (cancelled) {
      listener.run();
      return;
    }
    listeners.add(listener);
  }
}
