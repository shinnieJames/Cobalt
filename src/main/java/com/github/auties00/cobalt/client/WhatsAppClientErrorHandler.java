package com.github.auties00.cobalt.client;

import com.github.auties00.cobalt.exception.WhatsAppException;
import com.github.auties00.cobalt.exception.WhatsAppReconnectionException;
import com.github.auties00.cobalt.model.jid.Jid;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.BiConsumer;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.WARNING;

/**
 * A handler interface for managing error scenarios that occur within the WhatsApp API.
 * <p>
 * This interface enables customizable error handling strategies for different types of failures
 * that can occur during API operations, such as network issues, authentication errors,
 * cryptographic failures, and stream processing problems.
 * <p>
 * The handler determines how the application should respond to these errors through the
 * {@link Result} enum, which supports actions like discarding errors, disconnecting,
 * reconnecting, or logging out completely.
 * <p>
 * Several predefined error handlers are provided through static factory methods that implement
 * common error handling patterns, including logging to the terminal or saving to files.
 */
@SuppressWarnings("unused")
@FunctionalInterface
public interface WhatsAppClientErrorHandler {
    /**
     * Processes an error that occurred within the WhatsApp API.
     * <p>
     * When an error occurs in any component of the API, this method is called with details
     * about the exception that was thrown. The implementation should
     * evaluate the error context and determine the appropriate response action.
     *
     * @param whatsapp  the WhatsApp API instance where the error occurred
     * @param exception the exception that occurred
     * @return a {@link Result} value indicating how the API should respond to the error
     */
    Result handleError(WhatsAppClient whatsapp, WhatsAppException exception);

    /**
     * Creates an error handler that logs errors to the terminal's standard error.
     * <p>
     * This handler prints full stack traces to the console, making it suitable for
     * debugging and development environments.
     *
     * @return a new error handler that prints exceptions to the terminal
     */
    @SuppressWarnings("CallToPrintStackTrace")
    static WhatsAppClientErrorHandler toTerminal() {
        return defaultErrorHandler((api, error) -> error.printStackTrace());
    }

    /**
     * Creates an error handler that saves error information to files in the default location.
     * <p>
     * This handler saves detailed error information to files in the $HOME/.cobalt/errors directory,
     * making it useful for production environments where logs need to be preserved.
     *
     * @return a new error handler that persists exceptions to files
     */
    static WhatsAppClientErrorHandler toFile() {
        return toFile(Path.of(System.getProperty("user.home"), ".cobalt", "errors"));
    }

    /**
     * Creates an error handler that saves error information to files in a specified directory.
     * <p>
     * This handler works like {@link #toFile()} but allows specifying a custom directory
     * where error logs will be saved.
     *
     * @param directory the directory where error files should be saved
     * @return a new error handler that persists exceptions to the specified directory
     */
    static WhatsAppClientErrorHandler toFile(Path directory) {
        return defaultErrorHandler((api, throwable) -> Thread.startVirtualThread(() -> {
            var stackTraceWriter = new StringWriter();
            try(var stackTracePrinter = new PrintWriter(stackTraceWriter)) {
                var path = directory.resolve(System.currentTimeMillis() + ".txt");
                throwable.printStackTrace(stackTracePrinter);
                Files.writeString(path, stackTraceWriter.toString(), StandardOpenOption.CREATE);
            } catch (IOException exception) {
                throw new UncheckedIOException("Cannot serialize exception", exception);
            }
        }));
    }

    private static WhatsAppClientErrorHandler defaultErrorHandler(BiConsumer<WhatsAppClient, WhatsAppException> printer) {
        return (whatsapp, exception) -> {
            var logger = System.getLogger("ErrorHandler");
            var jid = whatsapp.store()
                    .jid()
                    .map(Jid::user)
                    .orElse("UNKNOWN");
            if(exception instanceof WhatsAppReconnectionException) {
                logger.log(WARNING, "[{0}] Cannot reconnect: retrying on next timeout", jid);
                return Result.DISCARD;
            }

            if (exception instanceof com.github.auties00.cobalt.exception.WhatsAppSessionException.Reconnect) {
                logger.log(WARNING, "[{0}] Session requires reconnect", jid);
                if (printer != null) {
                    printer.accept(whatsapp, exception);
                }
                return Result.RECONNECT;
            }

            if (exception instanceof com.github.auties00.cobalt.exception.WhatsAppSessionException.LoggedOut) {
                logger.log(WARNING, "[{0}] Session logged out by server", jid);
                if (printer != null) {
                    printer.accept(whatsapp, exception);
                }
                return Result.LOG_OUT;
            }

            if (exception instanceof com.github.auties00.cobalt.exception.WhatsAppSessionException.Banned) {
                logger.log(WARNING, "[{0}] Session banned by server", jid);
                if (printer != null) {
                    printer.accept(whatsapp, exception);
                }
                return Result.BAN;
            }

            var fatal = exception.isFatal();
            logger.log(ERROR, "[{0}] Socket failure at {1}: {2} failure", jid, exception.getClass().getSimpleName(), fatal ? "Fatal" : "Ignored");
            if (printer != null) {
                printer.accept(whatsapp, exception);
            }
            return fatal ? Result.DISCONNECT : Result.DISCARD;
        };
    }

    /**
     * Defines the possible response actions when handling errors.
     * <p>
     * These values determine how the API should proceed after encountering an error,
     * ranging from ignoring the error to terminating the session completely.
     */
    enum Result {
        /**
         * Indicates that the error should be ignored, allowing the session to continue
         */
        DISCARD,

        /**
         * Indicates that the current session should be disconnected but preserved for future use
         */
        DISCONNECT,

        /**
         * Indicates that the session should be disconnected and immediately reconnected
         */
        RECONNECT,

        /**
         * Indicates that the current session should be terminated as banned.
         */
        BAN,

        /**
         * Indicates that the current session should be completely terminated and deleted
         */
        LOG_OUT
    }
}
