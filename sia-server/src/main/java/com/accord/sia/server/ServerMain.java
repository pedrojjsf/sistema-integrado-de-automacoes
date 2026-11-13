package com.accord.sia.server;

import com.accord.sia.core.config.ConfigLoader;
import com.accord.sia.core.config.SiaConfig;
import java.nio.file.Path;
import java.util.logging.Logger;

/** Entrada do daemon: {@code java -jar sia-server.jar [config.yml]}. */
public final class ServerMain {

  private static final Logger LOG = Logger.getLogger(ServerMain.class.getName());

  private ServerMain() {}

  public static void main(String[] args) throws Exception {
    Path configPath = Path.of(args.length > 0 ? args[0] : "config/local.yml");
    SiaConfig config = new ConfigLoader().read(configPath);
    SiaServer server = SiaServer.create(config);
    Runtime.getRuntime().addShutdownHook(new Thread(server::close, "sia-shutdown"));
    server.start(config.server().host(), config.server().port());

    LOG.info(() ->
        "SIA ouvindo em http://"
            + config.server().host()
            + ":"
            + config.server().port()
            + " (auth="
            + config.auth().mode()
            + ", plugins="
            + server.core().plugins().size()
            + ", banco="
            + server.core().databasePath()
            + ")");
    Thread.currentThread().join();
  }
}
