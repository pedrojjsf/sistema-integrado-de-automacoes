package com.accord.sia.server;

import com.accord.sia.core.run.RunEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Formata eventos SSE (Server-Sent Events) em JSON Lines por evento. */
final class SseWriter {

  private SseWriter() {}

  static void write(OutputStream output, RunEvent event) throws IOException {
    String payload =
        "{\"type\":\""
            + escape(event.type())
            + "\",\"level\":\""
            + escape(event.level() == null ? "" : event.level())
            + "\",\"message\":\""
            + escape(event.message())
            + "\"}";
    frame(output, event.type(), payload);
  }

  static void finished(OutputStream output, String status) throws IOException {
    frame(output, "finished", "{\"status\":\"" + escape(status) + "\"}");
  }

  static void frame(OutputStream output, String eventName, String json) throws IOException {
    String frame = "event: " + eventName + "\ndata: " + json + "\n\n";
    output.write(frame.getBytes(StandardCharsets.UTF_8));
    output.flush();
  }

  static String escape(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
  }
}
