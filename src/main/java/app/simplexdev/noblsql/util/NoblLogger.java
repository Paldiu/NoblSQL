package app.simplexdev.noblsql.util;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;

public final class NoblLogger {
    private static Logger logger;

    private NoblLogger() {}

    public static void init(final Logger slf4jLogger) {
        logger = slf4jLogger;
    }

    public static Logger get() {
        return logger;
    }

    public static void info(final String message) {
        logger.info(message);
    }

    public static void info(final String message, final Object... args) {
        logger.info(message, args);
    }

    public static void warn(final String message) {
        logger.warn(message);
    }

    public static void warn(final String message, final Object... args) {
        logger.warn(message, args);
    }

    public static void warn(final String message, final Throwable throwable) {
        logger.warn("{}\n{}", message, ExceptionUtils.getStackTrace(throwable));
    }

    public static void error(final String message) {
        logger.error(message);
    }

    public static void error(final String message, final Object... args) {
        logger.error(message, args);
    }

    public static void error(final String message, final Throwable throwable) {
        logger.error("{}\n{}", message, ExceptionUtils.getStackTrace(throwable));
    }

    public static void debug(final String message, final Object... args) {
        logger.debug(message, args);
    }
}
