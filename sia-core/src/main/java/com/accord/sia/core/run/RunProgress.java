package com.accord.sia.core.run;

import com.accord.sia.api.ProgressSink;
import com.accord.sia.api.RunId;

/** Progresso de execucao gravado como log estruturado (vira evento SSE no daemon). */
public final class RunProgress implements ProgressSink {

  private final RunRepository repository;
  private final Redactor redactor;
  private final RunId runId;

  public RunProgress(RunRepository repository, Redactor redactor, RunId runId) {
    this.repository = repository;
    this.redactor = redactor;
    this.runId = runId;
  }

  @Override
  public void progress(int percent, String message) {
    int bounded = Math.max(0, Math.min(100, percent));
    repository.appendLog(
        runId, "PROGRESS", null, bounded + "% " + redactor.redact(message == null ? "" : message));
  }

  @Override
  public void step(String name) {
    repository.appendLog(runId, "STEP", null, redactor.redact(name == null ? "" : name));
  }
}
