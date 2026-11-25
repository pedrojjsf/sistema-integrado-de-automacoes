package com.accord.sia.core.process;

import com.accord.sia.api.PluginPermission;
import com.accord.sia.api.ProcessException;
import com.accord.sia.api.ProcessRequest;
import com.accord.sia.api.ProcessResult;
import com.accord.sia.api.ProcessRunner;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Executa processos externos sob politica do Core.
 *
 * <p>Garantias: binario resolvido pela allowlist, permissao {@code process} exigida, stdout/stderr
 * drenados em paralelo (evita deadlock de pipe), saida limitada a 256 KB, valores sensiveis
 * mascarados e arvore de processos morta no timeout.
 */
public final class DefaultProcessRunner implements ProcessRunner {

  private static final int MAX_CAPTURE_BYTES = 256 * 1024;
  private static final Duration KILL_GRACE = Duration.ofSeconds(5);

  private final ProcessAllowlist allowlist;
  private final PermissionCheck permissions;

  public DefaultProcessRunner(ProcessAllowlist allowlist, PermissionCheck permissions) {
    this.allowlist = allowlist;
    this.permissions = permissions;
  }

  @Override
  public ProcessResult run(ProcessRequest request) {
    permissions.require(PluginPermission.PROCESS);
    ProcessAllowlist.Spec spec = allowlist.spec(request.executable());

    List<String> command = new ArrayList<>(spec.baseCommand());
    command.addAll(request.arguments());
    Duration timeout = request.timeout() == null ? spec.defaultTimeout() : request.timeout();

    Process process;
    try {
      ProcessBuilder builder = new ProcessBuilder(command);
      if (request.workingDirectory() != null) {
        builder.directory(request.workingDirectory().toFile());
      }
      SafeEnvironment.apply(builder);
      process = builder.start();
    } catch (IOException error) {
      throw new ProcessException(
          ProcessException.Reason.NOT_FOUND,
          "nao foi possivel iniciar '" + request.executable() + "'",
          error);
    }

    Instant start = Instant.now();
    try (ExecutorService readers = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<String> stdout = readers.submit(() -> capture(process.getInputStream()));
      Future<String> stderr = readers.submit(() -> capture(process.getErrorStream()));

      boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (!finished) {
        killTree(process);
      }
      int exitCode = finished ? process.exitValue() : -1;

      return new ProcessResult(
          exitCode,
          redact(await(stdout), request.redactValues()),
          redact(await(stderr), request.redactValues()),
          Duration.between(start, Instant.now()),
          !finished);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      killTree(process);
      throw new ProcessException(ProcessException.Reason.IO, "execucao interrompida", error);
    }
  }

  private static String await(Future<String> future) {
    try {
      return future.get(KILL_GRACE.toSeconds(), TimeUnit.SECONDS);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      return "";
    } catch (ExecutionException | java.util.concurrent.TimeoutException error) {
      return "";
    }
  }

  /** Drena o stream inteiro para nao travar o filho, mas guarda so os primeiros 256 KB. */
  private static String capture(InputStream stream) throws IOException {
    ByteArrayOutputStream kept = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    try (InputStream input = stream) {
      int read;
      while ((read = input.read(buffer)) != -1) {
        int room = MAX_CAPTURE_BYTES - kept.size();
        if (room > 0) {
          kept.write(buffer, 0, Math.min(room, read));
        }
      }
    }
    return kept.toString(StandardCharsets.UTF_8);
  }

  private static String redact(String output, List<String> values) {
    String result = output;
    for (String value : values) {
      if (value != null && value.length() >= 3) {
        result = result.replace(value, "***");
      }
    }
    return result;
  }

  private static void killTree(Process process) {
    process.descendants().forEach(ProcessHandle::destroyForcibly);
    process.destroyForcibly();
    try {
      process.waitFor(KILL_GRACE.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
    }
  }
}
