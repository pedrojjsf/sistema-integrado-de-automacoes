package com.accord.sia.api;

import java.util.List;

/**
 * Contrato que todo plugin implementa.
 *
 * <p>O Core roda {@link #initialize(PluginContext)} uma vez apos carregar, deriva o catalogo de
 * {@link #automations()} e chama {@link #shutdown()} ao descarregar. Nenhum metodo recebe o Core
 * inteiro — apenas as fachadas expostas pelo contexto.
 */
public interface AutomationPlugin {

  /** Metadados; precisa ser estavel durante toda a vida do plugin. */
  PluginMetadata metadata();

  /**
   * Automacoes expostas, ja instanciadas.
   *
   * <p>Declarativo: nao faca IO aqui. O Core le {@code describe()} para validar o contrato e guarda
   * a instancia para executar.
   */
  List<Automation> automations();

  /** Inicializacao unica, apos a carga. Falhar aqui move o plugin para {@code FAILED}. */
  void initialize(PluginContext context);

  /** Libera recursos. Precisa ser idempotente: pode ser chamado mais de uma vez. */
  void shutdown();
}
