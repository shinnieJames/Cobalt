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
 * links or registers a {@link WhatsAppClient}.
 *
 * @apiNote
 * Two sub-hierarchies cover the supported flavours: {@link Web} drives
 * the companion linking ceremony for {@link WhatsAppClientType#WEB}
 * clients (QR scan or pairing-code entry), and {@link Mobile} drives
 * the registration ceremony for {@link WhatsAppClientType#MOBILE}
 * clients (SMS, voice, or in-app verification code). Implementations
 * are wired in via {@link WhatsAppClientBuilder}.
 *
 * @see WhatsAppClientBuilder
 */
public sealed interface WhatsAppClientVerificationHandler {
    /**
     * A verification handler for WhatsApp Web companion-device linking.
     *
     * @apiNote
     * Implementations surface the value the user must authorise on the
     * primary device: either a QR code payload (for {@link QrCode}) or
     * a short pairing code (for {@link PairingCode}).
     */
    sealed interface Web extends WhatsAppClientVerificationHandler {
        /**
         * Surfaces the verification value produced by the client to the
         * user.
         *
         * @apiNote
         * The value is either a QR code payload (for {@link QrCode}
         * handlers) or a short pairing code (for {@link PairingCode}
         * handlers).
         *
         * @param value the verification value produced by the client
         */
        void handle(String value);

        /**
         * A verification handler that renders the QR code produced
         * during the companion-linking flow.
         *
         * @apiNote
         * The handler receives the raw QR payload as a string. The
         * static factory methods provide common renderers (terminal,
         * temporary file, desktop viewer); custom rendering is
         * supported via a target-typed lambda.
         */
        @FunctionalInterface
        non-sealed interface QrCode extends Web {
            /**
             * Returns a handler that renders the QR code as ASCII art
             * on standard output.
             *
             * @apiNote
             * Useful in headless or CI environments. Terminals that do
             * not support UTF block-drawing characters render the
             * output as garbled symbols.
             *
             * @return the terminal-rendering handler
             */
            static QrCode toTerminal() {
                return qr -> {
                    var matrix = createMatrix(qr, 10, 0);
                    System.out.println(QrTerminal.toString(matrix, true));
                };
            }

            /**
             * Encodes a QR payload into a {@link BitMatrix} suitable
             * for rendering.
             *
             * @apiNote
             * Used internally by the static factory methods; exposed so
             * applications can render the matrix via a custom writer.
             *
             * @implNote
             * This implementation pins the error-correction level to
             * {@link ErrorCorrectionLevel#L} to maximise the data
             * capacity of the rendered code; WhatsApp's QR payloads are
             * comfortably within the level-{@code L} budget.
             *
             * @param qr     the payload to encode
             * @param size   the side length, in pixels, of the rendered
             *               square
             * @param margin the white margin around the rendered code,
             *               in modules
             * @return the encoded bit matrix
             * @throws UnsupportedOperationException if the payload
             *                                       cannot be encoded
             *                                       as a QR code
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
             * Returns a handler that writes the QR code to a temporary
             * JPEG file and forwards the file path to the supplied
             * consumer.
             *
             * @apiNote
             * Useful when the host process can render an image but not
             * ASCII art. The temporary file is created up front so the
             * write happens off the verification path.
             *
             * @param fileConsumer the consumer that receives the path
             *                     of the rendered file
             * @return the file-rendering handler
             * @throws UncheckedIOException if the temporary file cannot
             *                              be created
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
             * Returns a handler that writes the QR code to the supplied
             * path and forwards it to the supplied consumer.
             *
             * @apiNote
             * Use when the rendering target is a fixed path (e.g. a
             * shared volume or a web-served asset). The QR matrix is
             * generated at 500 pixels with a 5-module margin for
             * scan-friendliness.
             *
             * @param path         the destination path where the QR
             *                     code image is saved
             * @param fileConsumer the consumer that receives the path
             *                     of the rendered file
             * @return the file-rendering handler
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
             * A consumer that reacts to the file path where a QR code
             * has been rendered.
             *
             * @apiNote
             * Combine with a rendering target such as
             * {@link java.nio.file.Files#createTempFile(String, String, java.nio.file.attribute.FileAttribute[])}
             * to decide what to do with the resulting image: ignore it,
             * log its location, or open it in a desktop viewer.
             */
            interface ToFile extends Consumer<Path> {
                /**
                 * Returns a consumer that ignores the rendered file
                 * path and takes no action.
                 *
                 * @apiNote
                 * Useful when the application owns the file lifecycle
                 * elsewhere (for example a separate thread that polls
                 * the path).
                 *
                 * @return the no-op consumer
                 */
                static QrCode.ToFile discard() {
                    return ignored -> {};
                }

                /**
                 * Returns a consumer that logs the rendered file path
                 * through the system logger at {@link System.Logger.Level#INFO}.
                 *
                 * @return the logging consumer
                 */
                static QrCode.ToFile toTerminal() {
                    return path -> System.getLogger(QrCode.class.getName())
                            .log(INFO, "Saved QR code at %s".formatted(path));
                }

                /**
                 * Returns a consumer that opens the rendered file with
                 * the default desktop image viewer.
                 *
                 * @apiNote
                 * Silently no-ops on hosts where {@link Desktop} is not
                 * supported (typical headless servers). Throws if the
                 * viewer fails to launch on a supported host.
                 *
                 * @return the desktop-opening consumer
                 * @throws RuntimeException if the file cannot be opened
                 *                          via {@link Desktop}
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
         * produced during the companion-linking flow.
         *
         * @apiNote
         * Pairing codes are typed into the Linked Devices screen on
         * the primary device instead of scanning a QR code. The handler
         * receives the code as a plain string and is responsible for
         * presenting it.
         */
        @FunctionalInterface
        non-sealed interface PairingCode extends Web {
            /**
             * Returns a handler that prints the pairing code on
             * standard output.
             *
             * @return the terminal-printing handler
             */
            static PairingCode toTerminal() {
                return System.out::println;
            }
        }
    }

    /**
     * A verification handler for the WhatsApp mobile registration
     * flow.
     *
     * @apiNote
     * Mobile registration requires the user to receive a one-time code
     * on the phone number being registered and to feed it back into
     * the client. Implementations expose two decisions: which delivery
     * channel to request (SMS, voice call, in-app WhatsApp, or
     * server-chosen) and how to obtain the code once it has been
     * delivered. Optional callbacks handle CAPTCHA challenges and
     * two-factor PIN prompts.
     */
    non-sealed interface Mobile extends WhatsAppClientVerificationHandler {
        /**
         * Returns the preferred delivery channel for the verification
         * code.
         *
         * @apiNote
         * Supported values mirror the WhatsApp server-side method
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
         * @apiNote
         * Implementations typically block on user input (for example
         * reading a console line) and return the code once it has been
         * entered.
         *
         * @return the verification code
         */
        String verificationCode();

        /**
         * Solves a server-issued CAPTCHA challenge and returns the
         * user's answer.
         *
         * @apiNote
         * Called by the registration code when {@code /v2/code} or
         * {@code /v2/exist} returns an {@code image_blob} or
         * {@code audio_blob} payload, which happens whenever the
         * server places the request in the low-trust lane (typically
         * because no Play Integrity or App Attest token was submitted).
         * Returning {@link Optional#empty()} aborts registration with a
         * failure.
         *
         * @implSpec
         * The default implementation returns {@link Optional#empty()},
         * which the registration code treats as "caller cannot solve
         * challenges". Implementations that can prompt the user should
         * override this method.
         *
         * @param imagePng the PNG image challenge bytes, or
         *                 {@code null} if the server did not include
         *                 one
         * @param audioOgg the Ogg-encoded audio challenge bytes, or
         *                 {@code null} if the server did not include
         *                 one
         * @return the user-supplied answer, or empty to abort
         */
        default Optional<String> solveCaptcha(byte[] imagePng, byte[] audioOgg) {
            return Optional.empty();
        }

        /**
         * Returns the two-factor authentication PIN configured on the
         * account being registered.
         *
         * @apiNote
         * Called by the registration code when {@code /v2/register}
         * returns a {@code 2fa_required} reason. Returning
         * {@link Optional#empty()} aborts registration with a failure.
         *
         * @implSpec
         * The default implementation returns {@link Optional#empty()},
         * which the registration code treats as "caller cannot supply
         * a PIN". Implementations that can prompt the user should
         * override this method.
         *
         * @return the PIN, or empty to abort
         */
        default Optional<String> twoFactorPin() {
            return Optional.empty();
        }

        /**
         * Returns a verification handler that defers the choice of
         * delivery channel to the WhatsApp server and reads the
         * verification code from the supplied supplier.
         *
         * @apiNote
         * Use when the calling application has no preference; the
         * server picks the channel based on its own heuristics.
         *
         * @param supplier the supplier that produces the verification
         *                 code once the user has received it
         * @return the verification handler
         * @throws NullPointerException if {@code supplier} is
         *                              {@code null}
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
         * Returns a verification handler that requests SMS delivery
         * and reads the verification code from the supplied supplier.
         *
         * @param supplier the supplier that produces the verification
         *                 code once the user has received it
         * @return the verification handler
         * @throws NullPointerException if {@code supplier} is
         *                              {@code null}
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
         * Returns a verification handler that requests voice-call
         * delivery and reads the verification code from the supplied
         * supplier.
         *
         * @param supplier the supplier that produces the verification
         *                 code once the user has received it
         * @return the verification handler
         * @throws NullPointerException if {@code supplier} is
         *                              {@code null}
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
         * Returns a verification handler that requests in-app WhatsApp
         * delivery and reads the verification code from the supplied
         * supplier.
         *
         * @apiNote
         * Maps to the server-side {@code wa_old} method, which delivers
         * the code via an existing WhatsApp install on the same number.
         *
         * @param supplier the supplier that produces the verification
         *                 code once the user has received it
         * @return the verification handler
         * @throws NullPointerException if {@code supplier} is
         *                              {@code null}
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
