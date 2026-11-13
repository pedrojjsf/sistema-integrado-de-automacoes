package com.accord.sia.core.context;

import com.accord.sia.api.PluginContext;
import com.accord.sia.core.config.MapConfiguration;
import com.accord.sia.core.event.InProcessEventBus;
import com.accord.sia.core.logging.Slf4jLog;
import com.accord.sia.core.manifest.PluginManifest;
import com.accord.sia.core.permission.PermissionGuard;
import com.accord.sia.core.persistence.Database;
import com.accord.sia.core.persistence.SingleWriter;
import com.accord.sia.core.process.DefaultProcessRunner;
import com.accord.sia.core.process.ProcessAllowlist;
import com.accord.sia.core.secrets.EnvSecretAccess;
import com.accord.sia.core.storage.SqliteStorage;
import java.time.Clock;
import java.util.Map;

/**
 * Monta o {@link PluginContext} de um plugin.
 *
 * <p>Aqui esta o ponto em que as permissoes do manifesto viram objeto: um unico {@link
 * PermissionGuard} e compartilhado por todas as fachadas, entao nao existe caminho de servico que
 * escape da checagem.
 */
public final class PluginContextFactory {

  private final Database database;
  private final SingleWriter writer;
  private final ProcessAllowlist allowlist;
  private final InProcessEventBus events;
  private final Clock clock;

  public PluginContextFactory(
      Database database,
      SingleWriter writer,
      ProcessAllowlist allowlist,
      InProcessEventBus events,
      Clock clock) {
    this.database = database;
    this.writer = writer;
    this.allowlist = allowlist;
    this.events = events;
    this.clock = clock;
  }

  public PluginContext forPlugin(PluginManifest manifest, Map<String, String> config) {
    PermissionGuard guard = new PermissionGuard(manifest.id(), manifest.grantedPermissions());
    return new PluginContext(
        manifest.id(),
        new Slf4jLog(manifest.id()),
        new MapConfiguration(config),
        new SqliteStorage(database, writer, manifest.id(), clock),
        events,
        new EnvSecretAccess(manifest.id(), guard),
        new DefaultProcessRunner(allowlist, guard),
        clock);
  }
}
