package com.accord.sia.core.process;

/** Processo de teste que imprime os argumentos ou encerra com o codigo pedido. */
public final class EchoMain {

  private EchoMain() {}

  public static void main(String[] args) {
    if (args.length >= 2 && "exit".equals(args[0])) {
      System.exit(Integer.parseInt(args[1]));
    }
    System.out.println(String.join(" ", args));
  }
}
