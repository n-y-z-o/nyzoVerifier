package co.nyzo.verifier.util;

import java.io.File;

public class TestnetUtil {

    // The testnet file is always in the /var/lib/nyzo folder. This redirects all other paths to the testnet folder,
    // if present.
    public static final boolean testnet = new File("/var/lib/nyzo/activate_testnet").exists();
}
