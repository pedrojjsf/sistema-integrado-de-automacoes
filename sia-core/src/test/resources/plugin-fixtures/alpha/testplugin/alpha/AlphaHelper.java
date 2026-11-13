package testplugin.alpha;

/** Classe auxiliar que nao e carregada na inicializacao; usada para provar que o jar fechou. */
public final class AlphaHelper {

  private AlphaHelper() {}

  public static String version() {
    return "alpha-helper";
  }
}
