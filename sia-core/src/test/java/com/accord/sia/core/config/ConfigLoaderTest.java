package com.accord.sia.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class ConfigLoaderTest {

  @Test
  void leConfiguracaoCompleta() throws IOException {
    SiaConfig config =
        new ConfigLoader()
            .parse(
                """
                server:
                  host: 0.0.0.0
                  port: 9090
                database:
                  path: dados/sia.db
                plugins:
                  directory: meus-plugins
                auth:
                  mode: dev
                  tokens:
                    token-1:
                      subject: sub-1
                      name: Analista
                      roles: [admin, viewer]
                processes:
                  bot:
                    command: ["java", "-cp", "bot.jar", "Main"]
                    timeout-minutes: 5
                """);

    assertThat(config.server().host()).isEqualTo("0.0.0.0");
    assertThat(config.server().port()).isEqualTo(9090);
    assertThat(config.database().path()).isEqualTo("dados/sia.db");
    assertThat(config.plugins().directory()).isEqualTo("meus-plugins");
    assertThat(config.auth().tokens()).containsKey("token-1");
    assertThat(config.auth().tokens().get("token-1").roles()).containsExactly("admin", "viewer");
    assertThat(config.processes().get("bot").command()).hasSize(4);
    assertThat(config.processes().get("bot").timeoutMinutes()).isEqualTo(5);
  }

  @Test
  void aplicaDefaultsSeguros() throws IOException {
    SiaConfig config = new ConfigLoader().parse("{}");

    assertThat(config.server().host()).isEqualTo("127.0.0.1");
    assertThat(config.server().port()).isEqualTo(8080);
    assertThat(config.database().path()).isEqualTo("data/sia.db");
    assertThat(config.plugins().directory()).isEqualTo("plugins");
    assertThat(config.auth().mode()).isEqualTo("dev");
    assertThat(config.processes()).isEmpty();
  }

  @Test
  void rejeitaChaveDesconhecida() {
    assertThatThrownBy(() -> new ConfigLoader().parse("servidor: {}"))
        .isInstanceOf(IOException.class);
  }

  @Test
  void timeoutPadraoDoProcessoEh10Minutos() throws IOException {
    SiaConfig config =
        new ConfigLoader()
            .parse(
                """
                processes:
                  bot:
                    command: ["java", "-version"]
                """);

    assertThat(config.processes().get("bot").timeoutMinutes()).isEqualTo(10);
  }
}
