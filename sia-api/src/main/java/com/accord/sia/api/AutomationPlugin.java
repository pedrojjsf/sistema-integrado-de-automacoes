package com.accord.sia.api;

import java.util.List;

/**
 * Contrato que todo plugin implementa.
 *
 * <p>O Core chama {@link #initialize(PluginContext)} uma vez apos carregar a classe, usa {@link
 * #automations()} para montar o catalogo e chama {@link #shutdown()} ao descarregar. Nenhum metodo
 * recebe o Core inteiro - apenas as fachadas expostas pelo contexto.
 */
public interface AutomationPlugin {

  /** Metadados; precisa ser estavel durante toda a vida do plugin. */
  PluginMetadata metadata();

  /** Automacoes expostas. Declarativo: nao faca IO aqui. */
  List<AutomationDescriptor> automations();

  /** Inicializacao unica, apos a carga. Falhar aqui move o plugin para {@code FAILED}. */
  void initialize(PluginContext context);

  /** Libera recursos. Precisa ser idempotente: pode ser chamado mais de uma vez. */
  void shutdown();
}
