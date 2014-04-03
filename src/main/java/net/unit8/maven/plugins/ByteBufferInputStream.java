package net.unit8.maven.plugins;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * @author kawasima
 */
public class ByteBufferInputStream extends InputStream {
    private ByteBuffer buffer;

    public ByteBufferInputStream(ByteBuffer buffer) {
        this.buffer = buffer;
    }
    @Override
    public int read() throws IOException {
        if (!buffer.hasRemaining())
            return -1;
        return buffer.get();
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        int count = Math.min(buffer.remaining(), length);
        if (count == 0)
            return -1;
        buffer.get(bytes, offset, count);
        return count;
    }

    @Override
    public int available() throws IOException {
        return buffer.remaining();
    }
}
