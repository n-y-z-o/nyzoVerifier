package co.nyzo.verifier;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

public class EntryIpAddressManager {

    public static synchronized Set<ByteBuffer> addressesForCurrentVerifiers() {

        Set<ByteBuffer> addresses = new HashSet<>();
        

        return addresses;
    }
}
