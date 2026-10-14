package com.compression.compression_library.strategy;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;

public class DeflateCompressionProvider implements CompressionProvider {

    @Override
    public String encoding() {
        return "deflate";
    }

    /*
     * Lowest priority: when the client accepts several encodings with the
     * same quality, br (1) wins, then gzip (2), then deflate (3).
     */
    @Override
    public int serverPriority() {
        return 3;
    }

    /*
     * HTTP "deflate" means the zlib format (RFC 1950), which is what
     * DeflaterOutputStream produces by default. It is not raw deflate.
     */
    @Override
    public OutputStream createOutputStream(OutputStream outputStream)
            throws IOException {
        return new DeflaterOutputStream(outputStream);
    }

    /*
     * close() finishes the stream and also releases the native memory
     * used by the default Deflater.
     */
    @Override
    public void finish(OutputStream outputStream) throws IOException {
        outputStream.close();
    }
}