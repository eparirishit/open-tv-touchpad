package dev.opentvtouchpad.hidprobe;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Bounded transport framing; protobuf owns message decoding. */
final class TvWire {
    static final int MAX_FRAME = 65536;
    private TvWire() {}

    static byte[] read(InputStream input) throws IOException {
        long size = 0;
        for (int i = 0; i < 5; i++) {
            int b = input.read();
            if (b < 0) throw new EOFException("TV frame ended");
            size |= (long)(b & 127) << (i * 7);
            if ((b & 128) == 0) {
                if (size < 1 || size > MAX_FRAME || (i > 0 && (b & 127) == 0))
                    throw new IOException("Invalid TV frame length");
                byte[] bytes = new byte[(int)size];
                int offset = 0;
                while (offset < bytes.length) {
                    int count = input.read(bytes, offset, bytes.length - offset);
                    if (count <= 0) throw new EOFException("TV frame ended");
                    offset += count;
                }
                return bytes;
            }
        }
        throw new IOException("Invalid TV frame prefix");
    }

    static void write(OutputStream output, byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 1 || bytes.length > MAX_FRAME)
            throw new IOException("Invalid TV frame size");
        CodedOutputStream writer = CodedOutputStream.newInstance(output, 256);
        writer.writeUInt32NoTag(bytes.length);
        writer.writeRawBytes(bytes);
        writer.flush();
        output.flush();
    }

    static <T extends MessageLite> T parse(byte[] bytes, Parser<T> parser) throws IOException {
        if (bytes == null || bytes.length < 1 || bytes.length > MAX_FRAME)
            throw new IOException("Invalid TV message size");
        CodedInputStream reader = CodedInputStream.newInstance(bytes);
        reader.setRecursionLimit(16);
        reader.setSizeLimit(MAX_FRAME);
        T result = parser.parseFrom(reader);
        reader.checkLastTagWas(0);
        if (!reader.isAtEnd()) throw new IOException("Trailing TV message data");
        return result;
    }
}
