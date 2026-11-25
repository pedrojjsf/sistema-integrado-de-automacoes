package com.accord.sia.core.process;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SafeEnvironmentTest {

  @Test
  void removeSegredosSemTocarNoResto() {
    Map<String, String> environment = new HashMap<>();
    environment.put("PATH", "C:/bin");
    environment.put("SIA_SECRET_smtp_SENHA", "segredo");
    environment.put("SIA_SECRET_banco_TOKEN", "outro");
    environment.put("JAVA_HOME", "C:/jdk");

    SafeEnvironment.removeSecrets(environment);

    assertThat(environment).containsOnlyKeys("PATH", "JAVA_HOME");
  }
}
