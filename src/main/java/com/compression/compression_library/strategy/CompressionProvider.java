package com.compression.compression_library.strategy;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;

public interface CompressionProvider {

    String encoding();

    int serverPriority();

    OutputStream createOutputStream(
            HttpServletResponse response
    ) throws IOException;

    void finish(
            OutputStream outputStream
    ) throws IOException;
}
