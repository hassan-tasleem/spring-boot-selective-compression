package com.compression.compression_library.strategy;

import jakarta.servlet.http.HttpServletResponse;

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
            HttpServletResponse response
    ) throws IOException {

        return new GZIPOutputStream(
                response.getOutputStream()
        );
    }

    @Override
    public void finish(
            OutputStream outputStream
    ) throws IOException {

        GZIPOutputStream gzipOutputStream =
                (GZIPOutputStream) outputStream;

        gzipOutputStream.finish();
    }
}
