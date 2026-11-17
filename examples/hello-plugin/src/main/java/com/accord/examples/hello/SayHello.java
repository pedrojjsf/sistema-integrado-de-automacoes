package com.accord.examples.hello;

import com.accord.sia.api.Automation;
import com.accord.sia.api.AutomationContext;
import com.accord.sia.api.AutomationDescriptor;
import com.accord.sia.api.AutomationResult;
import com.accord.sia.api.ParamSpec;
import com.accord.sia.api.ParamType;
import java.util.List;
import java.util.Map;

/** Uma automacao, um parametro, uma saida: o menor exemplo util. */
public final class SayHello implements Automation {

  @Override
  public AutomationDescriptor describe() {
    return AutomationDescriptor.of(
        "hello.say",
        "Dizer ola",
        "Devolve uma saudacao para o nome informado",
        List.of(
            ParamSpec.required("nome", ParamType.STRING, "quem recebe a saudacao"),
            ParamSpec.optional("idioma", ParamType.STRING, "pt ou en", "pt")));
  }

  @Override
  public AutomationResult execute(AutomationContext context) {
    String nome = context.params().string("nome").orElse("mundo");
    String idioma = context.params().string("idioma", "pt");
    context.log().info("saudando " + nome);
    String saudacao = "en".equalsIgnoreCase(idioma) ? "Hello, " + nome : "Ola, " + nome;
    return AutomationResult.success(Map.of("saudacao", saudacao), "saudacao gerada");
  }
}
