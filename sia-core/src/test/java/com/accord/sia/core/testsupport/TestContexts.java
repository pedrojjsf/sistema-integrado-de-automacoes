package com.accord.sia.core.testsupport;

import com.accord.sia.api.Configuration;
import com.accord.sia.api.EventPublisher;
import com.accord.sia.api.Log;
import com.accord.sia.api.PluginContext;
import com.accord.sia.api.ProcessException;
import com.accord.sia.api.ProcessRunner;
import com.accord.sia.api.SecretAccess;
import com.accord.sia.api.Storage;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Contexto no-op (ou quase) para testes, com storage em memoria. */
public final class TestContexts {

  private TestContexts() {}

  public static PluginContext noop(String pluginId) {
    return new PluginContext(
        pluginId,
        memoryLog(),
        config(),
        memoryStorage(),
        events(),
        secrets(),
        noProcesses(),
        Clock.systemUTC());
  }

  public static Log memoryLog() {
    List<String> lines = new ArrayList<>();
    return new Log() {
      @Override
      public void debug(String message) {
        lines.add("DEBUG " + message);
      }

      @Override
      public void info(String message) {
        lines.add("INFO " + message);
      }

      @Override
      public void warn(String message) {
        lines.add("WARN " + message);
      }

      @Override
      public void error(String message) {
        lines.add("ERROR " + message);
      }

      @Override
      public void error(String message, Throwable error) {
        lines.add("ERROR " + message);
      }

      @Override
      public void event(String code, String message) {
        lines.add("EVENT " + code + " " + message);
      }
    };
  }

  public static Configuration config() {
    return new Configuration() {
      @Override
      public Optional<String> get(String key) {
        return Optional.empty();
      }

      @Override
      public String get(String key, String fallback) {
        return fallback;
      }
    };
  }

  public static Storage memoryStorage() {
    Map<String, String> values = new HashMap<>();
    return new Storage() {
      @Override
      public Optional<String> get(String key) {
        return Optional.ofNullable(values.get(key));
      }

      @Override
      public void put(String key, String value) {
        values.put(key, value);
      }

      @Override
      public void delete(String key) {
        values.remove(key);
      }

      @Override
      public List<String> keys(String prefix) {
        return values.keySet().stream().filter(key -> key.startsWith(prefix)).sorted().toList();
      }
    };
  }

  public static EventPublisher events() {
    return (type, payload) -> {};
  }

  public static SecretAccess secrets() {
    return name -> Optional.empty();
  }

  public static ProcessRunner noProcesses() {
    return request -> {
      throw new ProcessException(ProcessException.Reason.NOT_ALLOWED, "sem processos no teste");
    };
  }
}
