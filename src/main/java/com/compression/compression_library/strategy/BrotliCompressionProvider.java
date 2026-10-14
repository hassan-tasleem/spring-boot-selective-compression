package com.compression.compression_library.strategy;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.aayushatharva.brotli4j.encoder.BrotliOutputStream;
import com.aayushatharva.brotli4j.encoder.Encoder;

import java.io.IOException;
import java.io.OutputStream;

public class BrotliCompressionProvider implements CompressionProvider {

    /*
     * Brotli quality 0-11. The library default is 11 (best compression,
     * extremely slow). 4-5 is the normal choice for dynamic responses:
     * much faster, and still smaller than gzip.
     */
    private static final int QUALITY = 5;

    public BrotliCompressionProvider() {
        Brotli4jLoader.ensureAvailability();
    }

    @Override
    public String encoding() {
        return "br";
    }

    @Override
    public int serverPriority() {
        return 1;
    }

    @Override
    public OutputStream createOutputStream(OutputStream outputStream)
            throws IOException {
        Encoder.Parameters parameters = new Encoder.Parameters()
                .setQuality(QUALITY);

        return new BrotliOutputStream(outputStream, parameters);
    }

    @Override
    public void finish(OutputStream outputStream) throws IOException {
        outputStream.close();
    }
}