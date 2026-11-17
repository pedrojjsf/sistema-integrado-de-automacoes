package com.accord.sia.api;

/** Sinal de cancelamento cooperativo. */
public interface CancellationToken {

  boolean isCancelled();

  /**
   * Ponto de checagem: automacoes longas chamam isto entre iteracoes.
   *
   * @throws AutomationCancelledException quando o cancelamento ja foi solicitado
   */
  default void throwIfCancelled() {
    if (isCancelled()) {
      throw new AutomationCancelledException();
    }
  }

  /** Registra callback executado no momento do cancelamento. */
  void onCancel(Runnable listener);

  static CancellationToken none() {
    return new CancellationToken() {
      @Override
      public boolean isCancelled() {
        return false;
      }

      @Override
      public void onCancel(Runnable listener) {}
    };
  }
}
