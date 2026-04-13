import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientVerificationHandler;
import com.github.auties00.cobalt.client.WhatsAppWebClientHistory;

void main() throws IOException {
    WhatsAppClient.builder()
            .webClient()
            .loadLatestOrCreateConnection()
            .historySetting(WhatsAppWebClientHistory.extended(true))
            .unregistered(WhatsAppClientVerificationHandler.Web.QrCode.toTerminal())
            .connect()
            .waitForDisconnection();
}