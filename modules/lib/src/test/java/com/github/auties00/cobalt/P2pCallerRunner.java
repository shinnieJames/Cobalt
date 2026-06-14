import com.github.auties00.cobalt.call.stream.AudioFrame;
import com.github.auties00.cobalt.call.stream.AudioInputStream;
import com.github.auties00.cobalt.call.stream.AudioOutputStream;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientDevice;
import com.github.auties00.cobalt.model.call.Call;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.store.WhatsAppStoreFactory;

/**
 * Live caller-side gate for the direct same-network P2P media path: logs a Cobalt client in as a
 * desktop device (so the WhatsApp Desktop peer offers its {@code <transport>} candidates rather than
 * forcing relay-only), places an audio call to the desktop account, streams an MP3 outbound, and
 * measures the inbound audio peak amplitude. A {@code [P2P] direct media established} log followed by
 * {@code inbound maxAmp > 0} is the bidirectional-audio pass.
 */
public final class P2pCallerRunner {
    private static final String STORE = System.getenv().getOrDefault("COBALT_STORE", ".temp/cobalt-emu-store6");
    private static final long OWN_NUMBER = 19153544650L;
    private static final Jid PEER = Jid.of("393495089819@s.whatsapp.net");
    private static final java.nio.file.Path TRACK =
            java.nio.file.Path.of("C:\\Users\\Alessandro Autiero\\Downloads\\Brazy girls.mp3");

    private P2pCallerRunner() {
    }

    public static void main(String[] args) throws java.io.IOException {
        var deviceArg = System.getenv().getOrDefault("COBALT_DEVICE", "desktop");
        var device = "web".equalsIgnoreCase(deviceArg)
                ? LinkedWhatsAppClientDevice.web()
                : LinkedWhatsAppClientDevice.desktop();
        System.out.println("COBALT_P2P_CALLER store=" + STORE + " device=" + deviceArg);
        LinkedWhatsAppClient.builder()
                .webClient(WhatsAppStoreFactory.persistent(java.nio.file.Path.of(STORE)))
                .loadLatestOrCreateConnection()
                .device(device)
                .releaseChannel(com.github.auties00.cobalt.model.device.pairing.ClientPayload.ClientReleaseChannel.BETA)
                .unregistered(OWN_NUMBER, code -> System.out.println("COBALT_PAIRING_CODE=" + code))
                .addNodeReceivedListener((_, n) -> {
                    if (n.hasDescription("call")) {
                        System.out.println("RX-CALL " + n);
                    }
                })
                .addNodeSentListener((_, n) -> {
                    if (n.hasDescription("call")) {
                        System.out.println("TX-CALL " + n);
                    }
                })
                .addLoggedInListener(api -> {
                    if ("callee".equalsIgnoreCase(System.getenv().getOrDefault("COBALT_MODE", "caller"))) {
                        System.out.println("COBALT_LOGGED_IN (CALLEE); waiting for the desktop to call " + OWN_NUMBER);
                        if (System.getenv("COBALT_SENDHELLO") != null) {
                            Thread.ofVirtual().name("p2p-hello").start(() -> {
                                try {
                                    var key = ((LinkedWhatsAppClient) api).sendMessage(PEER,
                                            com.github.auties00.cobalt.model.message.MessageContainer.of(
                                                    "hello from cobalt (session bootstrap)"));
                                    System.out.println("HELLO_SENT id=" + key.id() + " -> " + PEER);
                                } catch (Exception e) {
                                    System.out.println("HELLO_SEND failed: " + e);
                                }
                            });
                        }
                    } else {
                        placeCall((LinkedWhatsAppClient) api);
                    }
                })
                .addCallListener((api, incoming) -> {
                    System.out.println("INCOMING CALL id=" + incoming.callId() + " from " + incoming.peer());
                    Thread.ofVirtual().name("p2p-accept").start(() -> {
                        try {
                            var audioIn = AudioInputStream.buffered();
                            var call = api.acceptCall(incoming, AudioOutputStream.fromFile(TRACK), audioIn, null, null);
                            System.out.println("CALL_ACCEPTED id=" + call.callId());
                            measureInbound(call, audioIn);
                        } catch (RuntimeException e) {
                            System.out.println("acceptCall failed: " + e);
                        }
                    });
                })
                .addCallEndedListener((_, callId, from, reason) ->
                        System.out.printf("CALL_ENDED %s from %s reason=%s%n", callId, from, reason))
                .addDisconnectedListener((_, reason) -> System.out.println("DISCONNECTED " + reason))
                .connect()
                .waitForDisconnection();
    }

    private static void placeCall(LinkedWhatsAppClient client) {
        System.out.println("COBALT_LOGGED_IN; placing call to " + PEER);
        var loop = System.getenv("COBALT_LOOP") != null;
        Thread.ofVirtual().name("p2p-place-call").start(() -> {
            do {
                try {
                    var audioIn = AudioInputStream.buffered();
                    var call = client.startCall(PEER, AudioOutputStream.fromFile(TRACK), audioIn);
                    System.out.println("CALL_PLACED id=" + call.callId());
                    Thread.ofVirtual().name("p2p-measure").start(() -> measureInbound(call, audioIn));
                } catch (RuntimeException e) {
                    System.out.println("startCall failed: " + e);
                }
                if (loop) {
                    try {
                        Thread.sleep(55000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    System.out.println("LOOP re-placing call");
                }
            } while (loop);
        });
    }

    private static void measureInbound(Call call, AudioInputStream audioIn) {
        var frames = 0L;
        var maxAmplitude = 0;
        try {
            AudioFrame frame;
            while ((frame = audioIn.read()) != null) {
                frames++;
                for (var sample : frame.pcm()) {
                    maxAmplitude = Math.max(maxAmplitude, Math.abs(sample));
                }
                if (frames % 100 == 0) {
                    System.out.printf("inbound frames=%d maxAmp=%d (call %s)%n", frames, maxAmplitude, call.callId());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.printf("DONE inbound frames=%d maxAmp=%d => %s%n", frames, maxAmplitude,
                maxAmplitude > 0 ? "AUDIBLE (bidirectional audio OK)" : "SILENT");
    }
}
