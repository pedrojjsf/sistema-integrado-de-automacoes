package com.accord.sia.core.process;

/**
 * Processo de teste que imprime os argumentos, o diretorio atual ou encerra com o codigo pedido.
 */
public final class EchoMain {

  private EchoMain() {}

  public static void main(String[] args) {
    if (args.length >= 2 && "exit".equals(args[0])) {
      System.exit(Integer.parseInt(args[1]));
    }
    if (args.length >= 1 && "pwd".equals(args[0])) {
      System.out.println(System.getProperty("user.dir"));
      return;
    }
    System.out.println(String.join(" ", args));
  }
}
