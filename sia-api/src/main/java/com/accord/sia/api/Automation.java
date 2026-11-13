package com.accord.sia.api;

/**
 * Unidade executavel exposta por um plugin.
 *
 * <p>Um plugin pode registrar varias automacoes. O Core executa a automacao, nao o plugin: e por
 * isso que {@code execute} vive aqui e nao em {@link AutomationPlugin}.
 */
public interface Automation {

  /** Identificador qualificado, igual ao declarado em {@link AutomationDescriptor#id()}. */
  String id();

  /**
   * Executa a automacao.
   *
   * @param context servicos e dados da execucao atual
   * @return resultado com as chaves de saida (e nunca o conteudo sensivel)
   * @throws AutomationException quando a automacao falha de forma recuperavel pelo Core
   * @throws AutomationCancelledException quando o Core pediu cancelamento
   * @throws PermissionDeniedException quando o plugin usa um recurso sem permissao declarada
   */
  AutomationResult execute(AutomationContext context) throws AutomationException;
}
