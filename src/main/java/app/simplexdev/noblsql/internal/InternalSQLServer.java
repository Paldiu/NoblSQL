package app.simplexdev.noblsql.internal;

import app.simplexdev.noblsql.util.NoblLogger;
import org.h2.tools.Server;

import java.io.File;
import java.sql.SQLException;

/**
 * Manages an internal H2 SQL server for use by NoblSQL.
 * <p>
 * The server listens on localhost only, so it is not exposed to the network. It stores
 * data files in a "data" subdirectory of the plugin's data folder, which is
 * automatically created if it doesn't exist.
 * <p>
 * This class is intended for internal use by NoblSQL and should not be used directly
 * by plugin developers. Use {@link app.simplexdev.noblsql.NoblSQL} instead.
 * 
 * @implNote Never add "-tcpAllowOthers" or similar flags that expose the server to the network.
 */
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
     * 
     * @implNote Never add "-tcpAllowOthers" or similar flags that expose the server to the network.
     * 
     * @throws RuntimeException if the server fails to start
     */
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

    /**
     * Stops the internal H2 SQL server if it is running.
     */
    public void stop() {
        if (tcpServer != null && tcpServer.isRunning(false)) {
            tcpServer.stop();
            NoblLogger.info("Internal H2 SQL server stopped.");
        }
    }

    /**
     * @return true if the internal SQL server is currently running, false otherwise
     */
    public boolean isRunning() {
        return tcpServer != null && tcpServer.isRunning(false);
    }

    /**
     * @return the port on which the internal SQL server is listening
     */
    public int getPort() {
        return port;
    }
}
