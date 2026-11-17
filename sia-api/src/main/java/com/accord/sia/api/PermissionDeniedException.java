package com.accord.sia.api;

/** Um recurso do Core foi usado sem a permissao declarada no manifesto. */
public class PermissionDeniedException extends RuntimeException {

  public PermissionDeniedException(String permission, String resource) {
    super("permissao '" + permission + "' ausente para acessar " + resource);
  }
}
