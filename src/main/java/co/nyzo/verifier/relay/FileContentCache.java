package co.nyzo.verifier.relay;

public class FileContentCache {

    private byte[] contents;
    private long fileTimestamp;
    private long lastUsedTimestamp;

    public FileContentCache(byte[] contents,long fileTimestamp) {
        this.contents = contents;
        this.fileTimestamp = fileTimestamp;
        this.lastUsedTimestamp = System.currentTimeMillis();
    }

    public byte[] getContents() {
        return contents;
    }

    public long getFileTimestamp() {
        return fileTimestamp;
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
