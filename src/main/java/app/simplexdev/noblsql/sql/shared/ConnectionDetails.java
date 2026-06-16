package app.simplexdev.noblsql.sql.shared;

public record ConnectionDetails(
    String host,
    int port,
    String database,
    String username,
    String password,
    boolean requireSsl,
    int poolSize
) {}
