package com.accord.sia.core.run;

import com.accord.sia.api.ProgressSink;
import com.accord.sia.api.RunId;

/** Progresso de execucao gravado como log estruturado (vira evento SSE no daemon). */
public final class RunProgress implements ProgressSink {

  private final RunRepository repository;
  private final Redactor redactor;
  private final RunId runId;
  private final RunEventBus events;

  public RunProgress(RunRepository repository, Redactor redactor, RunId runId, RunEventBus events) {
    this.repository = repository;
    this.redactor = redactor;
    this.runId = runId;
    this.events = events;
  }

  @Override
  public void progress(int percent, String message) {
    int bounded = Math.max(0, Math.min(100, percent));
    String text = bounded + "% " + redactor.redact(message == null ? "" : message);
    repository.appendLog(runId, "PROGRESS", null, text);
    events.publish(RunEvent.progress(runId.value(), text, java.time.Instant.now()));
  }

  @Override
  public void step(String name) {
    String text = redactor.redact(name == null ? "" : name);
    repository.appendLog(runId, "STEP", null, text);
    events.publish(RunEvent.step(runId.value(), text, java.time.Instant.now()));
  }
}
