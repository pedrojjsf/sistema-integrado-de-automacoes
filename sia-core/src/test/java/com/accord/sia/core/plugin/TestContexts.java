package com.accord.sia.core.plugin;

import com.accord.sia.api.Configuration;
import com.accord.sia.api.EventPublisher;
import com.accord.sia.api.Log;
import com.accord.sia.api.PluginContext;
import com.accord.sia.api.ProcessException;
import com.accord.sia.api.ProcessRunner;
import com.accord.sia.api.SecretAccess;
import com.accord.sia.api.Storage;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

/** Contexto no-op para testes de carregamento, sem banco nem servicos reais. */
final class TestContexts {

  private TestContexts() {}

  static PluginContext noop(String pluginId) {
    return new PluginContext(
        pluginId, log(), config(), storage(), events(), secrets(), processes(), Clock.systemUTC());
  }

  private static Log log() {
    return new Log() {
      @Override
      public void debug(String message) {}

      @Override
      public void info(String message) {}

      @Override
      public void warn(String message) {}

      @Override
      public void error(String message) {}

      @Override
      public void error(String message, Throwable error) {}

      @Override
      public void event(String code, String message) {}
    };
  }

  private static Configuration config() {
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

  private static Storage storage() {
    return new Storage() {
      @Override
      public Optional<String> get(String key) {
        return Optional.empty();
      }

      @Override
      public void put(String key, String value) {}

      @Override
      public void delete(String key) {}

      @Override
      public List<String> keys(String prefix) {
        return List.of();
      }
    };
  }

  private static EventPublisher events() {
    return (type, payload) -> {};
  }

  private static SecretAccess secrets() {
    return name -> Optional.empty();
  }

  private static ProcessRunner processes() {
    return request -> {
      throw new ProcessException(ProcessException.Reason.NOT_ALLOWED, "sem processos no teste");
    };
  }
}
