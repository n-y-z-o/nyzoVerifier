package co.nyzo.verifier.relay;

public class FileContentCache {

    private byte[] contents;
    private long lastUsedTimestamp;

    public FileContentCache(byte[] contents) {
        this.contents = contents;
        this.lastUsedTimestamp = System.currentTimeMillis();
    }

    public byte[] getContents() {
        return contents;
    }

    public long getLastUsedTimestamp() {
        return lastUsedTimestamp;
    }

    public void setLastUsedTimestamp() {
        this.lastUsedTimestamp = System.currentTimeMillis();
    }

    public long getContentLength() {
        return contents.length;
    }
}
