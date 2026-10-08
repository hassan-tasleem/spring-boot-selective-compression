package com.compression.compression_library.strategy;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.aayushatharva.brotli4j.encoder.BrotliOutputStream;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;

public class BrotliCompressionProvider
        implements CompressionProvider {

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
    public OutputStream createOutputStream(
            OutputStream outputStream
    ) throws IOException {

        return new BrotliOutputStream(
                outputStream
        );
    }

    @Override
    public void finish(
            OutputStream outputStream
    ) throws IOException {

        outputStream.close();
    }
}