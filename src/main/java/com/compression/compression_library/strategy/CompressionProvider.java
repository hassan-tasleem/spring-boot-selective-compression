package com.compression.compression_library.strategy;

import java.io.IOException;
import java.io.OutputStream;

public interface CompressionProvider {

    String encoding();

    int serverPriority();

    OutputStream createOutputStream(
            OutputStream outputStream
    ) throws IOException;

    void finish(
            OutputStream outputStream
    ) throws IOException;
}