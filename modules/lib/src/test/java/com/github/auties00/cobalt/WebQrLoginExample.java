import com.github.auties00.cobalt.call.stream.AudioInputStream;
import com.github.auties00.cobalt.call.stream.AudioOutputStream;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientVerificationHandler;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientDevice;
import com.github.auties00.cobalt.client.linked.WhatsAppWebClientHistory;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.store.WhatsAppStoreFactory;

/**
 * Runnable example that logs a Web client in by printing a QR code to the terminal, then registers
 * listeners that report sync progress (contacts, chats, newsletters, history, app-state actions)
 * and send a message to a named chat once chats arrive; run it as a single-file program through the
 * launcher protocol.
 */
void main() throws IOException {
    System.out.println("Hello World");
    LinkedWhatsAppClient.builder()
            .webClient(WhatsAppStoreFactory.persistent())
            .createConnection()
            .device(LinkedWhatsAppClientDevice.web())
            .historySetting(WhatsAppWebClientHistory.standard(false))
            .unregistered(19153544650L, LinkedWhatsAppClientVerificationHandler.Web.PairingCode.toTerminal())
            .addLoggedInListener(client -> {
                var api = (LinkedWhatsAppClient) client;
                System.out.printf("Connected: %s%n", api.store().settingsStore().privacySettings());
                var peer = Jid.of("393668765864@s.whatsapp.net");
                var call = api.startCall(peer, AudioOutputStream.buffered(), AudioInputStream.buffered());
                System.out.println("Call started: " + call.callId());
            })
            .addWebAppPrimaryFeaturesListener((_, features) -> System.out.printf("Received features: %s%n", features))
            .addNewMessageListener((_, message) -> System.out.println(message))
            .addContactsListener((_, contacts) -> System.out.printf("Contacts: %s%n", contacts.size()))
            .addChatsListener((api, chats) -> System.out.printf("Chats: %s%n", chats.size()))
            .addNewslettersListener((_, newsletters) -> System.out.printf("Newsletters: %s%n", newsletters.size()))
            .addNodeReceivedListener((_, incoming) -> System.out.printf("Received node %s%n", incoming))
            .addNodeSentListener((_, outgoing) -> System.out.printf("Sent node %s%n", outgoing))
            .addWebAppStateActionListener((_, action, info) -> System.out.printf("New action: %s, info: %s%n", action, info))
            .addMessageStatusListener((_, info) -> System.out.printf("Message status update for %s%n", info.key().id()))
            .addWebHistorySyncMessagesListener((_, chats, last) -> {
                for (var chat : chats) {
                    System.out.printf("%s now has %s messages (oldest message: %s)%n", chat.name(), chat.messageCount(), chat.oldestMessage().flatMap(ChatMessageInfo::timestamp).orElse(null));
                }
                System.out.printf("History sync chunk: %s chats, %s%n", chats.size(), last ? "done" : "waiting for more");
            })
            .addDisconnectedListener((_, reason) -> System.out.printf("Disconnected: %s%n", reason))
            .connect()
            .waitForDisconnection();
}