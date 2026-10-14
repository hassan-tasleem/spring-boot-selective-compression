package com.compression.compression_library.negotiation;

public record EncodingPreference(
        String encoding,
        double quality,
        int clientOrder) {
}