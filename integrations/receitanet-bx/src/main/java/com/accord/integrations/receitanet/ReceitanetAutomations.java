package com.accord.integrations.receitanet;

import com.accord.sia.api.AutomationContext;
import com.accord.sia.api.AutomationDescriptor;
import com.accord.sia.api.AutomationException;
import com.accord.sia.api.AutomationResult;
import com.accord.sia.api.ParamSpec;
import com.accord.sia.api.ParamType;
import com.accord.sia.api.Parameters;
import com.accord.sia.api.ProcessResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** As seis automacoes expostas pelo plugin do Receitanet BX. */
final class ReceitanetAutomations {

  private ReceitanetAutomations() {}

  static List<com.accord.sia.api.Automation> all() {
    return List.of(
        new Diag(), new Catalogo(), new Lote(), new Pedidos(), new Pendentes(), new Baixar());
  }

  /** Diagnostico de ambiente, banco e certificados. */
  static final class Diag extends BotAutomation {

    private static final Pattern TOTAL = Pattern.compile("total:\\s*(\\d+)");

    Diag() {
      super(
          AutomationDescriptor.of(
              "receitanet.diag", "Diagnostico", "Valida ambiente, banco Derby e certificados"),
          Duration.ofMinutes(5));
    }

    @Override
    protected List<String> arguments(Parameters params) {
      return List.of("diag");
    }

    /**
     * O diag retorna {@code -1} mesmo funcionando (avisos de papel nao encontrado), entao o
     * veredito aqui vem do conteudo, nao do exit code.
     */
    @Override
    protected AutomationResult interpret(ProcessResult result, AutomationContext context) {
      if (result.timedOut()) {
        return super.interpret(result, context);
      }
      boolean looksHealthy = result.stdout().contains("Receitanet BX");
      if (!looksHealthy) {
        return super.interpret(result, context);
      }
      Integer sistemas = sistemas(result.stdout());
      Map<String, Object> outputs =
          sistemas == null ? Map.of("exitCode", result.exitCode()) : Map.of("sistemas", sistemas);
      return AutomationResult.success(outputs, "diagnostico executado");
    }

    private static Integer sistemas(String stdout) {
      Matcher matcher = TOTAL.matcher(stdout);
      return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }
  }

  /** Sincroniza sistemas e papeis com a Receita (usa rede, nao pede certificado). */
  static final class Catalogo extends BotAutomation {

    Catalogo() {
      super(
          AutomationDescriptor.of(
              "receitanet.catalogo", "Sincronizar catalogo", "Atualiza sistemas e papeis"),
          Duration.ofMinutes(10));
    }

    @Override
    protected List<String> arguments(Parameters params) {
      return List.of("catalogo");
    }
  }

  /** Fluxo principal: lista de CNPJs x sistemas x periodo. */
  static final class Lote extends BotAutomation {

    Lote() {
      super(
          AutomationDescriptor.of(
              "receitanet.lote",
              "Lote por CNPJ",
              "Cria os pedidos do periodo e, opcionalmente, baixa os arquivos",
              List.of(
                  ParamSpec.required(
                      "arquivo", ParamType.STRING, "arquivo .txt com um CNPJ por linha"),
                  ParamSpec.required(
                      "sistemas", ParamType.STRING, "ids dos sistemas, separados por virgula"),
                  ParamSpec.required("inicio", ParamType.STRING, "data inicial (dd/MM/aaaa)"),
                  ParamSpec.required("fim", ParamType.STRING, "data final (dd/MM/aaaa)"),
                  ParamSpec.optional(
                      "baixar", ParamType.BOOLEAN, "baixa os arquivos apos criar os pedidos"),
                  ParamSpec.optional(
                      "limiteCnpjs", ParamType.INTEGER, "processa apenas os N primeiros CNPJs"),
                  ParamSpec.optional("dryRun", ParamType.BOOLEAN, "monta o XML sem enviar nada"))),
          Duration.ofMinutes(60));
    }

    @Override
    protected List<String> arguments(Parameters params) throws AutomationException {
      String arquivo =
          BotArguments.readableFile(
                  BotArguments.requireText(params.string("arquivo").orElse(null), "arquivo"))
              .toString();
      String sistemas =
          String.join(
              ",",
              BotArguments.systemIds(
                  BotArguments.requireText(params.string("sistemas").orElse(null), "sistemas")));
      String inicio =
          BotArguments.date(
                  BotArguments.requireText(params.string("inicio").orElse(null), "inicio"),
                  "inicio")
              .format(BotArguments.DATE);
      String fim =
          BotArguments.date(
                  BotArguments.requireText(params.string("fim").orElse(null), "fim"), "fim")
              .format(BotArguments.DATE);

      List<String> args =
          new ArrayList<>(
              List.of(
                  "lote-cnpj",
                  "--arquivo",
                  arquivo,
                  "--sistemas",
                  sistemas,
                  "--inicio",
                  inicio,
                  "--fim",
                  fim));
      if (params.bool("baixar", false)) {
        args.add("--baixar");
      }
      int limite = params.integer("limiteCnpjs", 0);
      if (limite > 0) {
        args.add("--limite-cnpjs");
        args.add(Integer.toString(limite));
      }
      if (params.bool("dryRun", false)) {
        args.add("--dry-run");
      }
      return List.copyOf(args);
    }
  }

  /** Lista pedidos do banco local. */
  static final class Pedidos extends BotAutomation {

    Pedidos() {
      super(
          AutomationDescriptor.of(
              "receitanet.pedidos",
              "Listar pedidos",
              "Lista os pedidos do banco local",
              List.of(
                  ParamSpec.optional(
                      "filtro", ParamType.STRING, "1=ativos, 0=encerrados, t=todos", "1"),
                  ParamSpec.optional("limite", ParamType.INTEGER, "quantos pedidos listar", "50"))),
          Duration.ofMinutes(5));
    }

    @Override
    protected List<String> arguments(Parameters params) throws AutomationException {
      String filtro = params.string("filtro", "1").trim();
      if (!List.of("1", "0", "t").contains(filtro)) {
        throw new AutomationException("PARAMETRO_INVALIDO", "filtro: use 1, 0 ou t");
      }
      int limite = params.integer("limite", 50);
      return List.of("pedidos", "--filtro", filtro, "--limite", Integer.toString(limite));
    }
  }

  /** Lista os arquivos que faltam baixar. */
  static final class Pendentes extends BotAutomation {

    Pendentes() {
      super(
          AutomationDescriptor.of(
              "receitanet.pendentes", "Pendentes", "Lista os arquivos que faltam baixar"),
          Duration.ofMinutes(5));
    }

    @Override
    protected List<String> arguments(Parameters params) {
      return List.of("pendentes");
    }
  }

  /** Enfileira e baixa arquivos: por pedido ou todos os pendentes. */
  static final class Baixar extends BotAutomation {

    Baixar() {
      super(
          AutomationDescriptor.of(
              "receitanet.baixar",
              "Baixar arquivos",
              "Enfileira e baixa por pedido ou todos os pendentes",
              List.of(
                  ParamSpec.optional("pedido", ParamType.STRING, "id do pedido"),
                  ParamSpec.optional(
                      "todos", ParamType.BOOLEAN, "varre os pedidos e baixa o que falta"),
                  ParamSpec.optional(
                      "timeoutSegundos", ParamType.INTEGER, "espera maxima do download", "900"))),
          Duration.ofMinutes(30));
    }

    @Override
    protected List<String> arguments(Parameters params) throws AutomationException {
      String pedido = params.string("pedido").orElse("");
      boolean todos = params.bool("todos", false);
      if (pedido.isBlank() == !todos) {
        throw new AutomationException(
            "PARAMETRO_INVALIDO", "informe 'pedido' ou 'todos', nunca os dois nem nenhum");
      }
      int timeout = params.integer("timeoutSegundos", 900);
      if (!pedido.isBlank()) {
        return List.of("baixar", "--pedido", pedido, "--timeout", Integer.toString(timeout));
      }
      return List.of("baixar", "--todos", "--timeout", Integer.toString(timeout));
    }
  }
}
