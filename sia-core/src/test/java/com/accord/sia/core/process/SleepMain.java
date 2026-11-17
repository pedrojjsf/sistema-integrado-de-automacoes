package com.accord.sia.core.process;

/** Processo de teste que dorme, para exercitar timeout e kill de arvore. */
public final class SleepMain {

  private SleepMain() {}

  public static void main(String[] args) throws InterruptedException {
    Thread.sleep(30_000);
  }
}
