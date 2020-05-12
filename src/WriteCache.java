import java.io.IOException;


public class WriteCache {

    public static void write_cache(byte[] bytes, int offset, int len)
            throws IOException {
        for (int i = 0; i < len; i++)
            write_cache((int) bytes[offset + i]);
    }
    private static void write_cache(int c) throws IOException {
        HttpProxy.writeCache.write((char) c);
    }
}