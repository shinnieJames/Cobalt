import com.github.auties00.cobalt.call.stream.AudioInputStream;
import com.github.auties00.cobalt.call.stream.AudioOutputStream;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientDevice;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientVerificationHandler;
import com.github.auties00.cobalt.client.linked.WhatsAppWebClientHistory;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.store.WhatsAppStoreFactory;

/**
 * Runnable example that places an audio call and streams a local MP3 file as the outbound audio.
 *
 * <p>Logs a Web client in with a terminal QR code (reusing a persisted session when one exists), and
 * once connected places an audio-only call to {@code PEER} via the streams-based
 * {@link LinkedWhatsAppClient#startCall(com.github.auties00.cobalt.model.jid.JidProvider, AudioOutputStream, AudioInputStream)}
 * overload. The outbound audio is bound to {@link AudioOutputStream#fromFile(java.nio.file.Path)}, so the
 * bundled FFmpeg build decodes and resamples {@code TRACK} to the call's 16 kHz mono Opus profile and
 * ships it to the peer; the inbound audio is buffered and left unread. Passing no video streams keeps the
 * call audio-only. The call ends when the file is exhausted, the peer hangs up, or the program stops.
 * Run it as a single-file program through the launcher protocol.
 */
void main() throws IOException {
    var peer = Jid.of("393495089819@s.whatsapp.net");
    var track = Path.of("C:\\Users\\Alessandro Autiero\\Downloads\\Brazy girls.mp3");
    LinkedWhatsAppClient.builder()
            .webClient(WhatsAppStoreFactory.persistent(Path.of(".temp/cobalt-emu-desktop")))
            .loadLatestOrCreateConnection()
            .device(LinkedWhatsAppClientDevice.web())
            .releaseChannel(com.github.auties00.cobalt.model.device.pairing.ClientPayload.ClientReleaseChannel.BETA)
            .historySetting(WhatsAppWebClientHistory.standard(false))
            .unregistered(19153544650L, LinkedWhatsAppClientVerificationHandler.Web.PairingCode.toTerminal())
            .addLoggedInListener(client -> {
                var call = ((LinkedWhatsAppClient) client).startCall(peer,
                        AudioOutputStream.fromFile(track), AudioInputStream.buffered());
                System.out.printf("Calling %s, streaming %s (call %s)%n", peer, track.getFileName(), call.callId());
            })
            .addCallEndedListener((_, callId, from, reason) ->
                    System.out.printf("Call ended: %s from %s reason=%s%n", callId, from, reason))
            .addDisconnectedListener((_, reason) -> System.out.printf("Disconnected: %s%n", reason))
            .connect()
            .waitForDisconnection();
}
