package com.github.auties00.cobalt.client;

import com.github.auties00.cobalt.exception.WhatsAppABPropTypeMismatchException;
import com.github.auties00.cobalt.exception.WhatsAppAdvCheckException;
import com.github.auties00.cobalt.exception.WhatsAppAdvValidationException;
import com.github.auties00.cobalt.exception.WhatsAppConnectionException;
import com.github.auties00.cobalt.exception.WhatsAppCorruptedStoreException;
import com.github.auties00.cobalt.exception.WhatsAppDeviceSyncException;
import com.github.auties00.cobalt.exception.WhatsAppException;
import com.github.auties00.cobalt.exception.WhatsAppHistorySyncException;
import com.github.auties00.cobalt.exception.WhatsAppLidMigrationException;
import com.github.auties00.cobalt.exception.WhatsAppMalformedJidException;
import com.github.auties00.cobalt.exception.WhatsAppMediaException;
import com.github.auties00.cobalt.exception.WhatsAppMessageException;
import com.github.auties00.cobalt.exception.WhatsAppOwnDeviceListExpiredException;
import com.github.auties00.cobalt.exception.WhatsAppReconnectionException;
import com.github.auties00.cobalt.exception.WhatsAppRegistrationException;
import com.github.auties00.cobalt.exception.WhatsAppServerRuntimeException;
import com.github.auties00.cobalt.exception.WhatsAppSessionException;
import com.github.auties00.cobalt.exception.WhatsAppStreamException;
import com.github.auties00.cobalt.exception.WhatsAppWebAppStateSyncException;
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
 * A pluggable strategy for reacting to failures raised by a
 * {@link WhatsAppClient}.
 *
 * <p>Cobalt deliberately avoids hardcoding recovery behaviour: whenever the
 * socket, stream, Signal pipeline, media pipeline, or synchronisation
 * subsystem throws a subtype of {@link WhatsAppException}, the client
 * delegates to an error handler which decides whether to swallow the error,
 * tear the session down, force a reconnect, or treat the account as banned
 * or logged out. This is a core divergence from WhatsApp Web, which inlines
 * recovery logic at every throw site.
 *
 * <p>The interface is a functional interface so a single
 * {@link java.util.function.BiFunction}-style lambda can be provided as the
 * handler. Static factory methods
 * ({@link #toTerminal()}, {@link #toFile()}, {@link #toFile(Path)}) return
 * sensible default implementations that log the exception and return a
 * {@link Result} derived from {@link WhatsAppException#isFatal()} plus
 * recognised session-control exception subtypes.
 *
 * @see WhatsAppException
 * @see WhatsAppClient#handleFailure(WhatsAppException)
 */
@SuppressWarnings("unused")
@FunctionalInterface
public interface WhatsAppClientErrorHandler {
    /**
     * Processes an error raised by the WhatsApp client pipeline and returns
     * the recovery action that should follow.
     *
     * <p>Implementations are expected to be non-blocking: they are invoked
     * from the thread that caught the failure and their return value drives
     * immediate session-control decisions (disconnect, reconnect, ban, log
     * out). If a handler needs to perform I/O (for example, writing the
     * stack trace to disk) it should dispatch that work to another thread
     * as the provided defaults do.
     *
     * @param whatsapp  the client where the error originated; its
     *                  {@link WhatsAppClient#store()} can be consulted for
     *                  context such as the session JID
     * @param exception the exception that was raised
     * @return the recovery action to apply
     */
    Result handleError(WhatsAppClient whatsapp, WhatsAppException exception);

    /**
     * Returns an error handler that writes full stack traces to the
     * terminal's standard error stream.
     *
     * <p>This is the default handler used by the builder when no custom
     * handler is supplied. It is well suited to development and debugging
     * sessions where seeing the exception inline is helpful; production
     * deployments should prefer {@link #toFile()}.
     *
     * @return a handler that prints exceptions to {@code System.err}
     */
    @SuppressWarnings("CallToPrintStackTrace")
    static WhatsAppClientErrorHandler toTerminal() {
        return defaultErrorHandler((api, error) -> error.printStackTrace());
    }

    /**
     * Returns an error handler that persists stack traces to
     * {@code $HOME/.cobalt/errors}.
     *
     * <p>Each exception is written to a new file named with the current
     * timestamp in milliseconds, on a dedicated virtual thread so the
     * session-control decision is not delayed by the write.
     *
     * @return a handler that persists exceptions under the user home
     *         directory
     */
    static WhatsAppClientErrorHandler toFile() {
        return toFile(Path.of(System.getProperty("user.home"), ".cobalt", "errors"));
    }

    /**
     * Returns an error handler that persists stack traces to the supplied
     * directory.
     *
     * <p>Behaviour matches {@link #toFile()} except that files are written
     * under {@code directory}. The directory must exist or be creatable by
     * the running process; failure to write raises an
     * {@link UncheckedIOException} on the background thread.
     *
     * @param directory the directory where stack traces are written
     * @return a handler that persists exceptions under the given directory
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

    /**
     * Builds the shared error-handling policy used by the
     * {@link #toTerminal()} and {@link #toFile(Path)} factories, delegating
     * the stack-trace rendering to the supplied {@code printer}.
     *
     * <p>The returned handler maps every exception subtype to the
     * {@link Result} that best matches WhatsApp Web's native reaction for
     * the same underlying failure. The mapping is exhaustive over the sealed
     * {@link WhatsAppException} hierarchy:
     *
     * <ul>
     *   <li>{@link WhatsAppReconnectionException}: {@code DISCARD};
     *       WA Web's {@code socketLoop} waits for the next backoff tick.</li>
     *   <li>{@link WhatsAppSessionException.Banned}: {@code BAN};
     *       matches WA Web's {@code LogoutReason.AccountLocked} path.</li>
     *   <li>{@link WhatsAppSessionException.LoggedOut}: {@code LOG_OUT};
     *       matches WA Web's {@code WAWebHandleStreamError} path for stream
     *       code {@code 516} and {@code conflict type="device_removed"}
     *       ({@code WAWebCompanionRegUtils.startLogout}).</li>
     *   <li>{@link WhatsAppSessionException.Reconnect}: {@code RECONNECT};
     *       matches WA Web's stream code {@code 515} path
     *       ({@code WAWebCompanionRegUtils.startLogin} then
     *       {@code WAWebStartBackend.startBackend}).</li>
     *   <li>{@link WhatsAppSessionException.Conflict}: {@code DISCONNECT};
     *       matches WA Web's {@code conflict type="replaced"} path which
     *       calls {@code WAComms.stopComms()} without resuming the socket
     *       loop.</li>
     *   <li>{@link WhatsAppSessionException.BadMac},
     *       {@link WhatsAppSessionException.Closed},
     *       {@link WhatsAppStreamException}: {@code RECONNECT}; these
     *       collapse to WA Web's {@code "CLOSE_SOCKET"} resolution, which
     *       lets the {@code socketLoop} re-open the connection with fresh
     *       Noise state.</li>
     *   <li>{@link WhatsAppLidMigrationException}: {@code LOG_OUT};
     *       matches WA Web's {@code LogoutReason.LidMigration*} values.</li>
     *   <li>{@link WhatsAppOwnDeviceListExpiredException}: {@code LOG_OUT};
     *       matches WA Web's ADV check forced logout when the own device
     *       list exceeds the server-enforced staleness threshold.</li>
     *   <li>{@link WhatsAppWebAppStateSyncException.MissingKeyOnAllDevices}:
     *       {@code LOG_OUT}; matches WA Web's
     *       {@code SyncdFatalErrorType.MISSING_KEY_ON_ALL_CLIENTS} terminal
     *       state.</li>
     *   <li>{@link WhatsAppWebAppStateSyncException} (other subtypes):
     *       {@code DISCARD}; {@code SyncdFatalError} and
     *       {@code SyncdRetryableError} re-sync the affected collection
     *       without disconnecting the session.</li>
     *   <li>{@link WhatsAppMessageException},
     *       {@link WhatsAppMediaException}: {@code DISCARD}; WA Web
     *       emits a retry receipt or surfaces a UI error without tearing
     *       the session down.</li>
     *   <li>{@link WhatsAppAdvValidationException},
     *       {@link WhatsAppAdvCheckException},
     *       {@link WhatsAppDeviceSyncException},
     *       {@link WhatsAppHistorySyncException}: {@code DISCARD};
     *       these are per-request failures in WA Web.</li>
     *   <li>{@link WhatsAppMalformedJidException},
     *       {@link WhatsAppABPropTypeMismatchException},
     *       {@link WhatsAppServerRuntimeException}: {@code DISCARD};
     *       informational/validation errors that do not impact the session.</li>
     *   <li>{@link WhatsAppConnectionException},
     *       {@link WhatsAppCorruptedStoreException},
     *       {@link WhatsAppRegistrationException}: {@code DISCONNECT};
     *       no session exists (or can exist) to recover.</li>
     *   <li>Any other {@link WhatsAppException}: falls back to
     *       {@link WhatsAppException#isFatal()}; a fatal exception maps to
     *       {@code DISCONNECT}, a non-fatal one to {@code DISCARD}.</li>
     * </ul>
     *
     * @param printer consumer that renders the exception (for example to
     *                stderr or to a file); may be {@code null} to skip
     *                rendering entirely
     * @return a handler that logs, renders, and maps exceptions to a
     *         {@link Result}
     */
    private static WhatsAppClientErrorHandler defaultErrorHandler(BiConsumer<WhatsAppClient, WhatsAppException> printer) {
        return (whatsapp, exception) -> {
            var logger = System.getLogger("ErrorHandler");
            var jid = whatsapp.store()
                    .jid()
                    .map(Jid::user)
                    .orElse("UNKNOWN");

            if (exception instanceof WhatsAppReconnectionException) {
                logger.log(WARNING, "[{0}] Cannot reconnect: retrying on next timeout", jid);
                if (printer != null) {
                    printer.accept(whatsapp, exception);
                }
                return Result.DISCARD;
            }

            if (exception instanceof WhatsAppSessionException.Banned) {
                logger.log(ERROR, "[{0}] Session banned by server", jid);
                if (printer != null) {
                    printer.accept(whatsapp, exception);
                }
                return Result.BAN;
            }

            if (exception instanceof WhatsAppSessionException.LoggedOut) {
                logger.log(WARNING, "[{0}] Session logged out by server", jid);
                if (printer != null) {
                    printer.accept(whatsapp, exception);
                }
                return Result.LOG_OUT;
            }

            if (exception instanceof WhatsAppSessionException.Reconnect) {
                return Result.RECONNECT;
            }

            if (exception instanceof WhatsAppSessionException.Conflict) {
                logger.log(WARNING, "[{0}] Session replaced by another active session", jid);
                if (printer != null) {
                    printer.accept(whatsapp, exception);
                }
                return Result.DISCONNECT;
            }

            if (exception instanceof WhatsAppSessionException
                    || exception instanceof WhatsAppStreamException) {
                logger.log(ERROR, "[{0}] Session/stream failure ({1}): reconnecting",
                        jid, exception.getClass().getSimpleName());
                if (printer != null) {
                    printer.accept(whatsapp, exception);
                }
                return Result.RECONNECT;
            }

            if (exception instanceof WhatsAppLidMigrationException) {
                logger.log(WARNING, "[{0}] LID migration failed: {1}", jid, exception.getMessage());
                if (printer != null) {
                    printer.accept(whatsapp, exception);
                }
                return Result.LOG_OUT;
            }

            if (exception instanceof WhatsAppOwnDeviceListExpiredException) {
                logger.log(WARNING, "[{0}] Own device list expired: logging out", jid);
                if (printer != null) {
                    printer.accept(whatsapp, exception);
                }
                return Result.LOG_OUT;
            }

            if (exception instanceof WhatsAppWebAppStateSyncException.MissingKeyOnAllDevices) {
                logger.log(ERROR, "[{0}] Sync key missing on all devices: {1}",
                        jid, exception.getMessage());
                if (printer != null) {
                    printer.accept(whatsapp, exception);
                }
                return Result.LOG_OUT;
            }

            if (exception instanceof WhatsAppWebAppStateSyncException) {
                logger.log(WARNING, "[{0}] App state sync failure ({1}): resyncing collection",
                        jid, exception.getClass().getSimpleName());
                return Result.DISCARD;
            }

            if (exception instanceof WhatsAppMessageException) {
                logger.log(WARNING, "[{0}] Message failure: {1}", jid, exception.getMessage());
                return Result.DISCARD;
            }

            if (exception instanceof WhatsAppMediaException) {
                logger.log(WARNING, "[{0}] Media operation failed: {1}", jid, exception.getMessage());
                return Result.DISCARD;
            }

            if (exception instanceof WhatsAppAdvValidationException
                    || exception instanceof WhatsAppAdvCheckException) {
                logger.log(WARNING, "[{0}] ADV failure ({1}): {2}",
                        jid, exception.getClass().getSimpleName(), exception.getMessage());
                return Result.DISCARD;
            }

            if (exception instanceof WhatsAppDeviceSyncException) {
                logger.log(WARNING, "[{0}] USync failure: {1}", jid, exception.getMessage());
                return Result.DISCARD;
            }

            if (exception instanceof WhatsAppHistorySyncException) {
                logger.log(WARNING, "[{0}] History sync failure: {1}", jid, exception.getMessage());
                return Result.DISCARD;
            }

            if (exception instanceof WhatsAppMalformedJidException
                    || exception instanceof WhatsAppABPropTypeMismatchException
                    || exception instanceof WhatsAppServerRuntimeException) {
                logger.log(WARNING, "[{0}] {1}: {2}",
                        jid, exception.getClass().getSimpleName(), exception.getMessage());
                return Result.DISCARD;
            }

            if (exception instanceof WhatsAppConnectionException
                    || exception instanceof WhatsAppCorruptedStoreException
                    || exception instanceof WhatsAppRegistrationException) {
                logger.log(ERROR, "[{0}] Fatal failure ({1}): {2}",
                        jid, exception.getClass().getSimpleName(), exception.getMessage());
                if (printer != null) {
                    printer.accept(whatsapp, exception);
                }
                return Result.DISCONNECT;
            }

            var fatal = exception.isFatal();
            logger.log(ERROR, "[{0}] Unhandled {1} ({2})",
                    jid, exception.getClass().getSimpleName(), fatal ? "fatal" : "ignored");
            if (printer != null) {
                printer.accept(whatsapp, exception);
            }
            return fatal ? Result.DISCONNECT : Result.DISCARD;
        };
    }

    /**
     * Enumerates the recovery actions that an error handler can request.
     *
     * <p>The value returned from
     * {@link WhatsAppClientErrorHandler#handleError(WhatsAppClient, WhatsAppException)}
     * is translated by the client into a concrete
     * {@link WhatsAppClientDisconnectReason}: {@code DISCARD} leaves the
     * session running, whereas the other values disconnect with the
     * corresponding reason.
     */
    enum Result {
        /**
         * Swallows the error and leaves the session running.
         *
         * <p>Appropriate for transient faults that do not compromise the
         * session state.
         */
        DISCARD,

        /**
         * Tears the session down while preserving the credentials on disk.
         *
         * <p>The client emits
         * {@link WhatsAppClientDisconnectReason#DISCONNECTED} and does not
         * attempt to reconnect.
         */
        DISCONNECT,

        /**
         * Tears the session down and immediately re-establishes the
         * connection.
         *
         * <p>The client emits
         * {@link WhatsAppClientDisconnectReason#RECONNECTING} and, after
         * notifying listeners, calls {@link WhatsAppClient#connect()}
         * internally.
         */
        RECONNECT,

        /**
         * Terminates the session and treats the account as banned.
         *
         * <p>The client emits
         * {@link WhatsAppClientDisconnectReason#BANNED} and deletes the
         * session store; reconnection is not attempted.
         */
        BAN,

        /**
         * Terminates the session and logs the account out.
         *
         * <p>The client emits
         * {@link WhatsAppClientDisconnectReason#LOGGED_OUT} and deletes the
         * session store so that fresh credentials must be obtained before
         * the next connection.
         */
        LOG_OUT
    }
}
