package app.simplexdev.noblsql.internal;

import app.simplexdev.noblsql.util.NoblLogger;
import org.h2.tools.Server;

import java.io.File;
import java.sql.SQLException;

public final class InternalSQLServer {
    private final int port;
    private final File baseDir;
    private Server tcpServer;

    public InternalSQLServer(final int port, final File baseDir) {
        this.port = port;
        this.baseDir = baseDir;
    }

    /**
     * Starts the internal H2 SQL server. Listens on localhost only, so it's safe to use the
     * default H2 port (9092) without worrying about conflicts with other database servers or
     * exposing it to the network. The server will store data files in a "data"
     * subdirectory of the plugin's data folder, which is automatically created if it doesn't exist.
     */
    // NOTE: Never add "-tcpAllowOthers" or similar flags that expose the server to the network.
    public void start() {
        try {
            baseDir.mkdirs();
            tcpServer = Server.createTcpServer(
                "-tcpPort", String.valueOf(port),
                "-ifNotExists",
                "-baseDir", baseDir.getAbsolutePath()
            ).start();
            NoblLogger.info("Internal H2 SQL server started on port {} (localhost only).", port);
        } catch (final SQLException e) {
            throw new RuntimeException("Failed to start internal SQL server on port " + port, e);
        }
    }

    public void stop() {
        if (tcpServer != null && tcpServer.isRunning(false)) {
            tcpServer.stop();
            NoblLogger.info("Internal H2 SQL server stopped.");
        }
    }

    public boolean isRunning() {
        return tcpServer != null && tcpServer.isRunning(false);
    }

    public int getPort() {
        return port;
    }
}
