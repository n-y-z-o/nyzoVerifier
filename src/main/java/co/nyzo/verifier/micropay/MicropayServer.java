package co.nyzo.verifier.micropay;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.ClientDataManager;
import co.nyzo.verifier.client.ConsoleColor;
import co.nyzo.verifier.util.PreferencesUtil;
import co.nyzo.verifier.web.WebListener;

public class MicropayServer {

    public static final byte[] receiverIdentifier = Verifier.getIdentifier();

    private static final String callbackBaseUrlKey = "micropay_callback_base_url";
    private static final String callbackBaseUrl = PreferencesUtil.get(callbackBaseUrlKey);

    public static void main(String[] args) {

        RunMode.setRunMode(RunMode.MicropayServer);
        BlockFileConsolidator.start();

        boolean initializationError = false;
        if (callbackBaseUrl.isEmpty()) {
            System.out.println(ConsoleColor.Red + callbackBaseUrlKey + " must be set in preferences" +
                    ConsoleColor.reset);
            initializationError = true;
        }

        if (!initializationError) {
            // The Micropay server currently uses the client data manager. This may change in a future version if
            // differentiated functionality is required.
            ClientDataManager.start();

            // The transaction tracker is specific to the Micropay server. Currently, it only displays transactions to
            // this server's identifier as those transactions are received in blocks.
            TransactionTracker.start();

            // The web listener is the web server.
            WebListener.start();
        }
    }

    public static String getCallbackBaseUrl() {
        return callbackBaseUrl;
    }
}
