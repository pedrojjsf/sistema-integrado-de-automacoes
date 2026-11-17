package testplugin.beta;

/** Classe interna do plugin beta que o plugin alpha nunca deve conseguir enxergar. */
public final class BetaSecret {

  private BetaSecret() {}

  public static String value() {
    return "segredo-do-beta";
  }
}
