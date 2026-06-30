import com.github.auties00.cobalt.calls2.stream.AudioInput;
import com.github.auties00.cobalt.calls2.stream.AudioOutput;
import com.github.auties00.cobalt.calls2.stream.VideoInput;
import com.github.auties00.cobalt.calls2.stream.VideoOutput;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientDevice;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientVerificationHandler;
import com.github.auties00.cobalt.model.device.pairing.ClientPayload;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.store.linked.LinkedWhatsAppStoreFactory;

/**
 * Runnable example that places an audio call and streams a local MP3 file as the outbound audio.
 *
 * <p>Logs a Web client in with a terminal QR code (reusing a persisted session when one exists), and
 * once connected places an audio-only call to {@code PEER} via the streams-based
 * {@link LinkedWhatsAppClient#startCall(com.github.auties00.cobalt.model.jid.JidProvider, AudioOutput, AudioInput)}
 * overload. The outbound audio is bound to {@link AudioOutput#file(Path)}, so the
 * bundled FFmpeg build decodes and resamples {@code TRACK} to the call's 16 kHz mono Opus profile and
 * ships it to the peer; the inbound audio is buffered and left unread. Passing no video streams keeps the
 * call audio-only. The call ends when the file is exhausted, the peer hangs up, or the program stops.
 * Run it as a single-file program through the launcher protocol.
 */
void main() throws IOException {
    var peer = Jid.of("19153544650@s.whatsapp.net");
    var audio = Path.of("C:\\Users\\Alessandro Autiero\\Downloads\\Brazy girls.mp3");
    var video = Path.of("C:\\Users\\Alessandro Autiero\\Downloads\\file_example_MP4_1920_18MG.mp4");
    LinkedWhatsAppClient.builder()
            .webClient(LinkedWhatsAppStoreFactory.persistent(Path.of(".temp/cobalt-em1-desktop1")))
            .loadLatestOrCreateConnection()
            .device(LinkedWhatsAppClientDevice.web())
            .releaseChannel(ClientPayload.ClientReleaseChannel.BETA)
            .defaultHistory()
            .unregistered(LinkedWhatsAppClientVerificationHandler.Web.QrCode.toTerminal())
            .addChatsListener((client, _) -> {
                var chat = client.store()
                        .chatStore()
                        .chats()
                        .stream()
                        .filter(entry -> entry.name().filter(name -> name.equals("Cobalt call test")).isPresent())
                        .findFirst()
                        .orElse(null);
                if(chat != null) {
                    System.out.printf("Calling %s, streaming %s%n", chat, audio.getFileName());
                    var call = client.startCall(chat,
                            AudioOutput.file(audio), AudioInput.buffered(), VideoOutput.file(video), VideoInput.buffered());
                    System.out.printf("Called %s: %s%n", peer, call.callId());
                }
            })
            .addNodeReceivedListener((_, incoming) -> System.out.printf("Received stanza %s%n", incoming))
            .addNodeSentListener((_, outgoing) -> System.out.printf("Sent stanza %s%n", outgoing))
            .addCallEndedListener((_, callId, from, reason) ->
                    System.out.printf("Call ended: %s from %s reason=%s%n", callId, from, reason))
            .addDisconnectedListener((_, reason) -> System.out.printf("Disconnected: %s%n", reason))
            .connect()
            .waitForDisconnection();
}
