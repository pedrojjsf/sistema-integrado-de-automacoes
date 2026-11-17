package com.accord.sia.api;

/**
 * Unidade executavel exposta por um plugin.
 *
 * <p>Descricao e execucao vivem no mesmo objeto de proposito: assim o Catalogo nao pode divergir do
 * codigo — nao existe "descrevi uma coisa e implementei outra".
 */
public interface Automation {

  /** Metadados da automacao; e a fonte da verdade do id usado em {@link #execute}. */
  AutomationDescriptor describe();

  /** Atalho para {@code describe().id()}. */
  default String id() {
    return describe().id();
  }

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
