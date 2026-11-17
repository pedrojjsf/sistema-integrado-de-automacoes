package com.accord.sia.core.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.accord.sia.api.PermissionDeniedException;
import com.accord.sia.api.ProcessException;
import com.accord.sia.api.ProcessRequest;
import com.accord.sia.api.ProcessResult;
import com.accord.sia.api.ProcessRunner;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultProcessRunnerTest {

  private static final String JAVA_BIN =
      Path.of(
              System.getProperty("java.home"),
              "bin",
              System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
                  ? "java.exe"
                  : "java")
          .toString();
  private static final String TEST_CLASSES =
      Path.of("target", "test-classes").toAbsolutePath().toString();

  private ProcessAllowlist allowlist;
  private ProcessRunner runner;

  @BeforeEach
  void setUp() {
    allowlist =
        ProcessAllowlist.of(
            Map.of(
                "echo", List.of(JAVA_BIN, "-cp", TEST_CLASSES, EchoMain.class.getName()),
                "sleep", List.of(JAVA_BIN, "-cp", TEST_CLASSES, SleepMain.class.getName())));
    runner = new DefaultProcessRunner(allowlist, permission -> {});
  }

  @Test
  void executaComandoPermitidoEIncluiArgumentos() {
    ProcessResult result = runner.run(ProcessRequest.of("echo", "alfa", "beta"));

    assertThat(result.succeeded()).isTrue();
    assertThat(result.exitCode()).isZero();
    assertThat(result.stdout()).contains("alfa beta");
    assertThat(result.duration()).isPositive();
  }

  @Test
  void propagaCodigoDeSaida() {
    ProcessResult result = runner.run(ProcessRequest.of("echo", "exit", "3"));

    assertThat(result.exitCode()).isEqualTo(3);
    assertThat(result.succeeded()).isFalse();
  }

  @Test
  void redigeValorSensivelNaSaida() {
    ProcessResult result =
        runner.run(
            ProcessRequest.of("echo", "--pin", "segredo-123").withRedactedValues("segredo-123"));

    assertThat(result.stdout()).contains("--pin").contains("***");
    assertThat(result.stdout()).doesNotContain("segredo-123");
  }

  @Test
  void mataProcessoNoTimeout() {
    ProcessResult result =
        runner.run(ProcessRequest.of("sleep").withTimeout(Duration.ofMillis(600)));

    assertThat(result.timedOut()).isTrue();
    assertThat(result.exitCode()).isEqualTo(-1);
    assertThat(result.duration()).isLessThan(Duration.ofSeconds(10));
  }

  @Test
  void recusaExecutavelForaDaAllowlist() {
    assertThatThrownBy(() -> runner.run(ProcessRequest.of("powershell", "-c", "dir")))
        .isInstanceOfSatisfying(
            ProcessException.class,
            error -> assertThat(error.reason()).isEqualTo(ProcessException.Reason.NOT_ALLOWED))
        .hasMessageContaining("powershell");
  }

  @Test
  void exigePermissaoDeProcesso() {
    ProcessRunner denied =
        new DefaultProcessRunner(
            allowlist,
            permission -> {
              throw new PermissionDeniedException(permission.wireName(), "echo");
            });

    assertThatThrownBy(() -> denied.run(ProcessRequest.of("echo", "x")))
        .isInstanceOf(PermissionDeniedException.class);
  }

  @Test
  void respeitaDiretorioDeTrabalho() throws java.io.IOException {
    java.nio.file.Path workDir = java.nio.file.Files.createTempDirectory("sia-pwd");
    ProcessResult result =
        runner.run(ProcessRequest.of("echo", "pwd").withWorkingDirectory(workDir));

    java.nio.file.Path reported = java.nio.file.Path.of(result.stdout().strip());
    assertThat(reported.toRealPath()).isEqualTo(workDir.toRealPath());
  }

  @Test
  void allowlistRespondeConsultas() {
    assertThat(allowlist.allows("echo")).isTrue();
    assertThat(allowlist.allows("chrome")).isFalse();
  }
}
