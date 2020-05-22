package co.nyzo.verifier;

import co.nyzo.verifier.util.PrintUtil;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

public class ShortLong {

    // This class provides a backward-compatible version/height combination that does not change the beginning bytes of
    // blocks or balance lists. The layout is as follows:
    // (1) 2 bytes: version [0-0xFFFF]
    // (2) 6 bytes: block height [0-0xFFFFFFFFFFFF]

    // In decimal notation, this provides version numbers from 0 through 65535 and block heights from 0 through
    // 281,474,976,710,655. This allows for over 60 million years of blocks.

    public static final int maximumShortValue = 0xffff;
    public static final long maximumLongValue = 0xffffffffffffL;

    // An int is used to store the short value to provide a simple, full-range unsigned short.
    private int shortValue;
    private long longValue;

    public static void main(String[] args) {

        // This is a simple script to demonstrate that the maximum block height is not a reasonable concern. The output
        // is: 1970326373771385.000 (62439127-03-27 03:09:45.000 UTC).
        System.out.println(PrintUtil.printTimestamp(BlockManager.startTimestampForHeight(maximumLongValue)));
    }

    public ShortLong(int shortValue, long longValue) {
        this.shortValue = shortValue;
        this.longValue = longValue;
    }

    public int getShortValue() {
        return shortValue;
    }

    public long getLongValue() {
        return longValue;
    }

    public static ShortLong fromByteBuffer(ByteBuffer buffer) {
        long combinedValue = buffer.getLong();
        int shortValue = (int) ((combinedValue >> 48) & 0xffff);
        long longValue = combinedValue & 0xffffffffffffL;
        return new ShortLong(shortValue, longValue);
    }

    public static ShortLong fromFile(RandomAccessFile file) {
        long combinedValue = 0;
        try {
            combinedValue = file.readLong();
        } catch (Exception ignored) { }
        int shortValue = (int) ((combinedValue >> 48) & 0xffff);
        long longValue = combinedValue & 0xffffffffffffL;
        return new ShortLong(shortValue, longValue);
    }

    public static long combinedValue(int shortValue, long longValue) {
        return ((long) shortValue) << 48 | longValue;
    }
}
