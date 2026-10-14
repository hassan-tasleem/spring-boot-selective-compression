package com.compression.compression_library.filter;

import com.compression.compression_library.negotiation.CompressionNegotiator;
import com.compression.compression_library.wrapper.ConditionalCompressionResponseWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class CompressionFilter extends OncePerRequestFilter {

    private final CompressionNegotiator compressionNegotiator;

    public CompressionFilter(
            CompressionNegotiator compressionNegotiator
    ) {
        this.compressionNegotiator =
                compressionNegotiator;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        ConditionalCompressionResponseWrapper wrapper =
                new ConditionalCompressionResponseWrapper(
                        request,
                        response,
                        compressionNegotiator
                );

        try {
            filterChain.doFilter(
                    request,
                    wrapper
            );
        } finally {
            wrapper.finish();
        }
    }
}