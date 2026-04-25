package com.github.auties00.cobalt.client;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import it.auties.qr.QrTerminal;

import java.awt.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.google.zxing.client.j2se.MatrixToImageWriter.writeToPath;
import static java.lang.System.Logger.Level.INFO;
import static java.nio.file.Files.createTempFile;

/**
 * A pluggable strategy for completing the authentication ceremony that
 * links or registers a WhatsApp client.
 *
 * <p>The two sub-hierarchies address the two supported flavours of
 * authentication:
 * <ul>
 *   <li>{@link Web} handles the companion-device linking ceremony used by
 *       {@link WhatsAppClientType#WEB} clients, which can complete either
 *       by scanning a QR code on the primary device or by entering a
 *       pairing code;</li>
 *   <li>{@link Mobile} handles the registration ceremony used by
 *       {@link WhatsAppClientType#MOBILE} clients, which starts with an
 *       SMS, voice, or in-app WhatsApp verification code request.</li>
 * </ul>
 *
 * <p>Handlers are wired into a {@link WhatsAppClient} via the builder
 * ({@link WhatsAppClientBuilder.Options.Web#unregistered(WhatsAppClientVerificationHandler.Web.QrCode)}
 * and the Mobile {@code register} variants).
 *
 * @see WhatsAppClientBuilder
 */
public sealed interface WhatsAppClientVerificationHandler {
    /**
     * A verification handler for WhatsApp Web companion-device linking.
     *
     * <p>Implementations receive the value that the primary device must
     * authorise: either the payload encoded in a QR code that the user
     * scans on their phone, or a short pairing code that the user types
     * into the Linked Devices screen.
     */
    sealed interface Web extends WhatsAppClientVerificationHandler {
        /**
         * Receives the verification value produced by the Cobalt client
         * and surfaces it to the user.
         *
         * <p>The value is either a QR code payload (for
         * {@link QrCode} handlers) or a short pairing code (for
         * {@link PairingCode} handlers).
         *
         * @param value the verification value produced by the client
         */
        void handle(String value);

        /**
         * A verification handler that renders the QR code produced by
         * Cobalt during the companion-linking flow.
         *
         * <p>The handler receives the raw QR payload as a string; the
         * static factory methods provide common renderers (terminal,
         * temporary file, desktop viewer) so callers can pick a behaviour
         * without writing boilerplate.
         */
        @FunctionalInterface
        non-sealed interface QrCode extends Web {
            /**
             * Creates a handler that prints the QR code to the terminal.
             *
             * @return A QrCode handler that renders the QR code to the console
             * @apiNote If your terminal doesn't support UTF characters, the output may appear as random characters
             */
            static QrCode toTerminal() {
                return qr -> {
                    var matrix = createMatrix(qr, 10, 0);
                    System.out.println(QrTerminal.toString(matrix, true));
                };
            }

            /**
             * Creates a BitMatrix representation of a QR code from a value.
             *
             * @param qr     The QR code content to encode
             * @param size   The size of the QR code in pixels
             * @param margin The margin size around the QR code
             * @return A BitMatrix representing the QR code
             * @throws UnsupportedOperationException if the QR code cannot be created
             */
            static BitMatrix createMatrix(String qr, int size, int margin) {
                try {
                    var writer = new MultiFormatWriter();
                    return writer.encode(qr, BarcodeFormat.QR_CODE, size, size, Map.of(EncodeHintType.MARGIN, margin, EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L));
                } catch (WriterException exception) {
                    throw new UnsupportedOperationException("Cannot create QR code", exception);
                }
            }

            /**
             * Creates a handler that saves the QR code to a temporary file and processes it with the provided consumer.
             *
             * @param fileConsumer The consumer to process the created file path
             * @return A QrCode handler that saves the QR code to a temporary file
             * @throws UncheckedIOException if the temporary file cannot be created
             */
            static QrCode toFile(QrCode.ToFile fileConsumer) {
                try {
                    var file = createTempFile("qr", ".jpg");
                    return toFile(file, fileConsumer);
                } catch (IOException exception) {
                    throw new UncheckedIOException("Cannot create temp file for QR handler", exception);
                }
            }

            /**
             * Creates a handler that saves the QR code to a specified path and processes it with the provided consumer.
             *
             * @param path The destination path where the QR code image will be saved
             * @param fileConsumer The consumer to process the file path after creation
             * @return A QrCode handler that saves the QR code to the specified path
             */
            static QrCode toFile(Path path, QrCode.ToFile fileConsumer) {
                return qr -> {
                    try {
                        var matrix = createMatrix(qr, 500, 5);
                        writeToPath(matrix, "jpg", path);
                        fileConsumer.accept(path);
                    } catch (IOException exception) {
                        throw new UncheckedIOException("Cannot save QR code to file", exception);
                    }
                };
            }

            /**
             * A consumer that reacts to a file path where a QR code has
             * been rendered.
             *
             * <p>Callers combine a rendering path supplier (typically a
             * {@link Path} returned by
             * {@link java.nio.file.Files#createTempFile(String, String, java.nio.file.attribute.FileAttribute[])})
             * with a {@code ToFile} consumer to decide what to do with the
             * resulting image: ignore it, log its location, or open it in
             * a desktop viewer.
             */
            interface ToFile extends Consumer<Path> {
                /**
                 * Creates a consumer that discards the file path, taking no action.
                 *
                 * @return A ToFile consumer that ignores the file path
                 */
                static QrCode.ToFile discard() {
                    return ignored -> {};
                }

                /**
                 * Creates a consumer that logs the file path to the terminal using the system logger.
                 *
                 * @return A ToFile consumer that prints the file location to the console
                 */
                static QrCode.ToFile toTerminal() {
                    return path -> System.getLogger(QrCode.class.getName())
                            .log(INFO, "Saved QR code at %s".formatted(path));
                }

                /**
                 * Creates a consumer that opens the QR code file using the default desktop application.
                 *
                 * @return A ToFile consumer that opens the file with the desktop
                 * @throws RuntimeException if the file cannot be opened with the desktop
                 */
                static QrCode.ToFile toDesktop() {
                    return path -> {
                        try {
                            if (!Desktop.isDesktopSupported()) {
                                return;
                            }
                            Desktop.getDesktop().open(path.toFile());
                        } catch (Throwable throwable) {
                            throw new RuntimeException("Cannot open file with desktop", throwable);
                        }
                    };
                }
            }
        }

        /**
         * A verification handler that surfaces the short pairing code
         * produced by Cobalt during the companion-linking flow.
         *
         * <p>Pairing codes are typed into the Linked Devices screen on the
         * primary device instead of scanning a QR. The handler receives
         * the code as a plain string and is responsible for presenting it
         * to the user.
         */
        @FunctionalInterface
        non-sealed interface PairingCode extends Web {
            /**
             * Creates a handler that prints the pairing code to the terminal.
             *
             * @return A PairingCode handler that outputs the code to the console
             */
            static PairingCode toTerminal() {
                return System.out::println;
            }
        }
    }

    /**
     * A verification handler for the WhatsApp mobile registration flow.
     *
     * <p>Mobile registration requires the user to receive a one-time code
     * on the phone number being registered and to feed it back into the
     * client. Implementations expose two decisions: which delivery channel
     * to request (SMS, voice call, in-app WhatsApp, or server-chosen) and
     * how to obtain the code once it has been delivered.
     */
    non-sealed interface Mobile extends WhatsAppClientVerificationHandler {
        /**
         * Returns the preferred delivery channel for the verification
         * code.
         *
         * <p>Supported values mirror the WhatsApp server-side method
         * identifiers: {@code sms}, {@code voice}, and {@code wa_old}.
         * Returning {@link Optional#empty()} lets the server pick a
         * default channel.
         *
         * @return the preferred delivery method, or empty to defer the
         *         choice to the server
         */
        Optional<String> requestMethod();

        /**
         * Returns the verification code supplied by the user.
         *
         * <p>Implementations typically block on user input (e.g., reading
         * a console line) and return the code once it has been entered.
         *
         * @return the verification code
         */
        String verificationCode();

        /**
         * Solves a server-issued challenge (image or audio CAPTCHA) and
         * returns the user's answer.
         *
         * <p>Called by the registration code when {@code /v2/code} or
         * {@code /v2/exist} returns an {@code image_blob} or
         * {@code audio_blob} payload, which happens whenever the server
         * decides the client is in the low-trust lane (typically because
         * no Play Integrity / App Attest token was submitted). The
         * default implementation returns {@link Optional#empty()}, which
         * the registration treats as "caller cannot solve challenges" and
         * raises a registration failure.
         *
         * @param imagePng the PNG image challenge bytes, or {@code null}
         *                 if the server did not include one
         * @param audioOgg the Ogg-encoded audio challenge bytes, or
         *                 {@code null} if the server did not include one
         * @return the user-supplied answer, or empty to abort
         */
        default Optional<String> solveCaptcha(byte[] imagePng, byte[] audioOgg) {
            return Optional.empty();
        }

        /**
         * Returns the two-factor authentication PIN the user has
         * configured on the account being registered.
         *
         * <p>Called by the registration code when {@code /v2/register}
         * returns a {@code 2fa_required} reason. The default
         * implementation returns {@link Optional#empty()}, which the
         * registration treats as "caller cannot supply a PIN" and raises
         * a registration failure.
         *
         * @return the PIN, or empty to abort
         */
        default Optional<String> twoFactorPin() {
            return Optional.empty();
        }

        /**
         * Creates a Mobile verification handler with no specific request method.
         * The verification code is obtained from the provided supplier.
         *
         * @param supplier A non-null supplier that provides the verification code
         * @return A Mobile verification handler with no specific request method
         * @throws NullPointerException if the supplier is null
         */
        static Mobile none(Supplier<String> supplier) {
            Objects.requireNonNull(supplier, "supplier cannot be null");
            return new Mobile() {
                @Override
                public Optional<String> requestMethod() {
                    return Optional.empty();
                }

                @Override
                public String verificationCode() {
                    var value = supplier.get();
                    if(value == null) {
                        throw new IllegalArgumentException("Cannot send verification code: no value");
                    }
                    return value;
                }
            };
        }

        /**
         * Creates a Mobile verification handler that requests verification via SMS.
         * The verification code is obtained from the provided supplier.
         *
         * @param supplier A non-null supplier that provides the verification code
         * @return A Mobile verification handler for SMS verification
         * @throws NullPointerException if the supplier is null
         */
        static Mobile sms(Supplier<String> supplier) {
            Objects.requireNonNull(supplier, "supplier cannot be null");
            return new Mobile() {
                @Override
                public Optional<String> requestMethod() {
                    return Optional.of("sms");
                }

                @Override
                public String verificationCode() {
                    var value = supplier.get();
                    if(value == null) {
                        throw new IllegalArgumentException("Cannot send verification code: no value");
                    }
                    return value;
                }
            };
        }

        /**
         * Creates a Mobile verification handler that requests verification via phone call.
         * The verification code is obtained from the provided supplier.
         *
         * @param supplier A non-null supplier that provides the verification code
         * @return A Mobile verification handler for voice call verification
         * @throws NullPointerException if the supplier is null
         */
        static Mobile call(Supplier<String> supplier) {
            Objects.requireNonNull(supplier, "supplier cannot be null");
            return new Mobile() {
                @Override
                public Optional<String> requestMethod() {
                    return Optional.of("voice");
                }

                @Override
                public String verificationCode() {
                    var value = supplier.get();
                    if(value == null) {
                        throw new IllegalArgumentException("Cannot send verification code: no value");
                    }
                    return value;
                }
            };
        }

        /**
         * Creates a Mobile verification handler that requests verification via WhatsApp.
         * The verification code is obtained from the provided supplier.
         *
         * @param supplier A non-null supplier that provides the verification code
         * @return A Mobile verification handler for WhatsApp verification
         * @throws NullPointerException if the supplier is null
         */
        static Mobile whatsapp(Supplier<String> supplier) {
            Objects.requireNonNull(supplier, "supplier cannot be null");
            return new Mobile() {
                @Override
                public Optional<String> requestMethod() {
                    return Optional.of("wa_old");
                }

                @Override
                public String verificationCode() {
                    var value = supplier.get();
                    if(value == null) {
                        throw new IllegalArgumentException("Cannot send verification code: no value");
                    }
                    return value;
                }
            };
        }
    }
}