package com.compression.compression_library.strategy;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

public class GzipCompressionProvider
        implements CompressionProvider {

    @Override
    public String encoding() {
        return "gzip";
    }

    @Override
    public int serverPriority() {
        return 2;
    }

    @Override
    public OutputStream createOutputStream(
            OutputStream outputStream
    ) throws IOException {

        return new GZIPOutputStream(outputStream);
    }

    @Override
    public void finish(
            OutputStream outputStream
    ) throws IOException {

        GZIPOutputStream gzip =
                (GZIPOutputStream) outputStream;

        gzip.finish();
    }
}