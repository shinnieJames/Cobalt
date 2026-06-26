package com.github.auties00.cobalt.calls2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Adversarial P8 wiring oracle for the client construction and delegation seam.
 *
 * <p>The production {@code LiveLinkedWhatsAppClient} constructor wires the whole session stack, including
 * native connectivity monitors, so instantiating it in a unit test is not viable. This suite instead reads
 * the client and stanza-stream source the way {@code CallKeySignalSeamTest} reads the calls2 tree, and pins
 * the two structural P8 invariants that an instance test would otherwise prove: the {@link LiveCalls2Service}
 * is constructed before the stanza-stream service that registers the inbound receiver (a wrong order would
 * NPE on an inbound call), and the public call methods delegate to the calls2 service rather than the
 * legacy {@code call.CallService}.
 */
@DisplayName("calls2 P8 client wiring (source oracle)")
class Calls2ClientWiringSourceTest {
    private static final String CLIENT = "client/linked/LiveLinkedWhatsAppClient.java";
    private static final String STREAM = "stream/LiveNodeStreamService.java";

    @Test
    @DisplayName("LiveCalls2Service is constructed before LiveNodeStreamService in the client constructor")
    void constructionOrder() {
        var src = read(CLIENT);
        var calls2At = src.indexOf("new LiveCalls2Service(");
        var streamAt = src.indexOf("new LiveNodeStreamService(");
        assertTrue(calls2At >= 0, "client must construct LiveCalls2Service");
        assertTrue(streamAt >= 0, "client must construct LiveNodeStreamService");
        assertTrue(calls2At < streamAt,
                "LiveCalls2Service must be constructed before LiveNodeStreamService so the inbound call "
                        + "receiver never dispatches to a null service");
    }

    @Test
    @DisplayName("the client holds a Calls2Service field and passes it into the stanza-stream service")
    void serviceFieldAndHandoff() {
        var src = read(CLIENT);
        assertTrue(src.contains("private final Calls2Service calls2Service;"),
                "client must hold the call subsystem as a Calls2Service field");
        assertTrue(src.matches("(?s).*new LiveNodeStreamService\\(this, calls2Service,.*"),
                "client must pass calls2Service into the stanza-stream service constructor");
    }

    @Test
    @DisplayName("the stanza-stream service takes a Calls2Service and registers the calls2 receivers under call/terminate")
    void streamServiceRegistersCalls2Receivers() {
        var src = read(STREAM);
        assertTrue(src.contains("Calls2Service calls2Service"),
                "stanza-stream service constructor must take a Calls2Service, not a legacy CallService");
        assertTrue(src.matches("(?s).*addHandler\\(result, \"call\", new Calls2CallReceiver\\(.*"),
                "the \"call\" tag must be served by Calls2CallReceiver");
        assertTrue(src.matches("(?s).*addHandler\\(result, \"terminate\", new Calls2TerminateReceiver\\(.*"),
                "the \"terminate\" tag must be served by Calls2TerminateReceiver");
    }

    @Test
    @DisplayName("neither the client nor the stanza-stream service references the legacy CallService")
    void legacyCallServiceNotWired() {
        for (var file : new String[]{CLIENT, STREAM}) {
            var code = stripComments(read(file));
            assertTrue(!code.contains("CallService"),
                    file + " must not reference the legacy CallService/LiveCallService; calls2 replaces it");
            assertTrue(!code.contains("new CallReceiver(") && !code.contains("new CallTerminateReceiver("),
                    file + " must not construct the legacy CallReceiver/CallTerminateReceiver");
        }
    }

    @Test
    @DisplayName("the canonical public call methods delegate to calls2Service")
    void publicMethodsDelegateToCalls2() {
        var code = stripComments(read(CLIENT));
        // The start/accept/reject/terminate(Call)/mute(Call)/video/screen-share/interaction surface must
        // route through the service so the call rides the calls2 engine rather than a hand-built stanza.
        for (var call : new String[]{
                "calls2Service.placeCall(",
                "calls2Service.placeGroupCall(",
                "calls2Service.accept(",
                "calls2Service.reject(",
                "calls2Service.terminate(",
                "calls2Service.sendMute(",
                "calls2Service.sendVideoState(",
                "calls2Service.startScreenShare(",
                "calls2Service.stopScreenShare(",
                "calls2Service.sendInteraction("}) {
            assertTrue(code.contains(call),
                    "a public call method must delegate via " + call + " so the call uses the calls2 engine");
        }
    }

    @Test
    @DisplayName("the public call methods no longer build a legacy CallStanza")
    void publicMethodsNoLongerBuildLegacyStanza() {
        var code = stripComments(read(CLIENT));
        // P8 finished migrating the call-method delegators onto Calls2Service: the methods that once built a
        // legacy call.signaling.CallStanza (terminateCall(String), preacceptCall, muteCall(String) via
        // setCallMute, add/removeCallParticipants) now route through the service, so no CallStanza build
        // survives in the client.
        for (var legacy : new String[]{
                "CallStanza.terminate(",
                "CallStanza.preaccept(",
                "CallStanza.mute(",
                "CallStanza.groupUpdate("}) {
            assertTrue(!code.contains(legacy),
                    "a public call method still builds a legacy " + legacy + " instead of delegating to "
                            + "Calls2Service");
        }
    }

    private static Path libModuleRoot() {
        var suffix = Path.of("src", "main", "java", "com", "github", "auties00", "cobalt");
        var moduleSuffix = Path.of("modules", "lib").resolve(suffix);
        var start = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (var dir = start; dir != null; dir = dir.getParent()) {
            var local = dir.resolve(suffix);
            if (Files.isDirectory(local)) {
                return local;
            }
            var fromRepoRoot = dir.resolve(moduleSuffix);
            if (Files.isDirectory(fromRepoRoot)) {
                return fromRepoRoot;
            }
        }
        throw new IllegalStateException("could not locate the lib source tree from user.dir=" + start);
    }

    private static String read(String relativeToCobaltPackage) {
        var path = libModuleRoot().resolve(relativeToCobaltPackage);
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + path, e);
        }
    }

    /**
     * Removes block and line comments so a forbidden identifier mentioned only in javadoc or an inline
     * comment does not trip a scan; matches {@code CallKeySignalSeamTest}'s stripping.
     */
    private static String stripComments(String source) {
        var out = new StringBuilder(source.length());
        var inBlock = false;
        var inLine = false;
        for (var i = 0; i < source.length(); i++) {
            var c = source.charAt(i);
            var next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (inLine) {
                if (c == '\n') {
                    inLine = false;
                    out.append(c);
                }
                continue;
            }
            if (inBlock) {
                if (c == '*' && next == '/') {
                    inBlock = false;
                    i++;
                }
                continue;
            }
            if (c == '/' && next == '/') {
                inLine = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlock = true;
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}
