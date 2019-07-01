package co.nyzo.verifier.micropay;

import co.nyzo.verifier.util.PreferencesUtil;

public class MicropayAuthorization {

    // The default authorization time is one hour. This is not a system where users are making large investments in a
    // piece of content, and we do not want to impose the engineering burden of efficiently tracking a long history of
    // authorizations. If you paid 0.1 Nyzos for an article this morning, and you found it interesting enough to reread
    // the article this evening, paying another 0.1 Nyzos to reload the article is not unreasonable.
    private static final long authorizationTime = PreferencesUtil.getLong("micropay_authorization_time",
            1000L * 60L * 60L);

    // This class exists to structure additional authorization options, including number of accesses. However, in this
    // initial implementation, only expiration time is provided.
    private long expirationTime;

    public MicropayAuthorization() {
        this.expirationTime = System.currentTimeMillis() + authorizationTime;
    }

    public boolean isValid() {
        return System.currentTimeMillis() < expirationTime;
    }
}
