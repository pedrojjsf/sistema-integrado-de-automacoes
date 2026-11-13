package com.accord.sia.api;

/** Canal de progresso de uma execucao; no daemon vira evento SSE. */
public interface ProgressSink {

  /** Percentual de 0 a 100 e mensagem curta, sem dado sensivel. */
  void progress(int percent, String message);

  /** Marca o inicio de um passo nomeado (usado pelo workflow). */
  void step(String name);

  static ProgressSink noop() {
    return new ProgressSink() {
      @Override
      public void progress(int percent, String message) {}

      @Override
      public void step(String name) {}
    };
  }
}
