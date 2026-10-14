package com.compression.compression_library.wrapper;

import com.compression.compression_library.interceptor.CompressionInterceptor;
import com.compression.compression_library.negotiation.CompressionNegotiator;
import com.compression.compression_library.strategy.CompressionProvider;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

public class ConditionalCompressionResponseWrapper
        extends HttpServletResponseWrapper {

    private final HttpServletRequest request;
    private final CompressionNegotiator compressionNegotiator;

    /*
     * The whole body is buffered until finish(), so headers can
     * still be changed before anything reaches the real response.
     */
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    private ServletOutputStream outputStream;
    private PrintWriter writer;
    private boolean finished;

    private static final Set<String> COMPRESSIBLE_CONTENT_TYPES = Set.of(
            "application/json",
            "application/xml",
            "application/javascript",
            "application/x-javascript",
            "application/xhtml+xml",
            "application/rss+xml",
            "application/atom+xml",
            "application/soap+xml",
            "application/svg+xml",
            "application/wasm",
            "text/html",
            "text/plain",
            "text/css",
            "text/javascript",
            "text/xml",
            "text/csv"
    );

    public ConditionalCompressionResponseWrapper(
            HttpServletRequest request,
            HttpServletResponse response,
            CompressionNegotiator compressionNegotiator
    ) {
        super(response);
        this.request = request;
        this.compressionNegotiator = compressionNegotiator;
    }

    /* ---------------- decisions ---------------- */

    private boolean compressionRequested() {
        boolean requested = Boolean.TRUE.equals(
                request.getAttribute(CompressionInterceptor.COMPRESS_ATTRIBUTE)
        );
        if (!requested) {
            return false;
        }

        if ("HEAD".equalsIgnoreCase(request.getMethod())) {
            return false;
        }

        return !isBodylessStatus(((HttpServletResponse) getResponse()).getStatus());
    }

    private boolean isBodylessStatus(int status) {
        return status == HttpServletResponse.SC_NO_CONTENT
                || status == HttpServletResponse.SC_NOT_MODIFIED
                || (status >= 100 && status < 200);
    }

    private long getMinResponseSize() {
        Object value = request.getAttribute(
                CompressionInterceptor.MIN_SIZE_ATTRIBUTE
        );
        if (value instanceof Number number) {
            return Math.max(0, number.longValue());
        }
        return 1024;
    }

    private boolean isCompressibleContentType() {
        String contentType = getResponse().getContentType();
        if (contentType == null || contentType.isBlank()) {
            return false;
        }

        String mediaType = contentType
                .toLowerCase(Locale.ROOT)
                .split(";")[0]
                .trim();

        if (mediaType.startsWith("text/")) {
            return true;
        }
        return COMPRESSIBLE_CONTENT_TYPES.contains(mediaType);
    }

    private boolean alreadyEncoded() {
        HttpServletResponse response = (HttpServletResponse) getResponse();
        String contentEncoding = response.getHeader("Content-Encoding");

        return contentEncoding != null
                && !contentEncoding.isBlank()
                && !"identity".equalsIgnoreCase(contentEncoding);
    }

    /* ---------------- output ---------------- */

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (writer != null) {
            throw new IllegalStateException(
                    "getWriter() has already been called"
            );
        }

        if (outputStream == null) {
            outputStream = new ServletOutputStream() {
                @Override
                public void write(int b) throws IOException {
                    buffer.write(b);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    buffer.write(b, off, len);
                }

                @Override
                public void flush() throws IOException {
                    // intentionally empty: never flush the real response
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener listener) {
                    // synchronous processing only
                }
            };
        }
        return outputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (outputStream != null) {
            throw new IllegalStateException(
                    "getOutputStream() has already been called"
            );
        }

        if (writer == null) {
            writer = new PrintWriter(
                    getOutputStream(),
                    false,
                    StandardCharsets.UTF_8
            );
        }
        return writer;
    }

    @Override
    public void flushBuffer() throws IOException {
        if (writer != null) {
            writer.flush();
        }
    }

    @Override
    public void resetBuffer() {
        buffer.reset();
    }

    @Override
    public void reset() {
        buffer.reset();
        super.reset();
    }

    @Override
    public void setContentLength(int len) {
        // ignored
    }

    @Override
    public void setContentLengthLong(long len) {
        // ignored
    }

    @Override
    public void setHeader(String name, String value) {
        if ("Content-Length".equalsIgnoreCase(name)) {
            return;
        }
        super.setHeader(name, value);
    }

    @Override
    public void addHeader(String name, String value) {
        if ("Content-Length".equalsIgnoreCase(name)) {
            return;
        }
        super.addHeader(name, value);
    }

    @Override
    public void setIntHeader(String name, int value) {
        if ("Content-Length".equalsIgnoreCase(name)) {
            return;
        }
        super.setIntHeader(name, value);
    }

    @Override
    public void addIntHeader(String name, int value) {
        if ("Content-Length".equalsIgnoreCase(name)) {
            return;
        }
        super.addIntHeader(name, value);
    }

    /* ---------------- finish ---------------- */

    public void finish() throws IOException {
        if (finished) {
            return;
        }
        finished = true;

        if (writer != null) {
            writer.flush();
        }

        HttpServletResponse response = (HttpServletResponse) getResponse();


        if (response.isCommitted()) {
            return;
        }

        if (!compressionRequested()
                || !isCompressibleContentType()
                || alreadyEncoded()
                || buffer.size() < getMinResponseSize()) {
            writeUncompressed(response);
            return;
        }

        CompressionProvider provider = compressionNegotiator.negotiate(request);

        if (provider == null) {
            writeUncompressed(response);
            return;
        }

        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        OutputStream compressionStream = provider.createOutputStream(compressed);
        compressionStream.write(buffer.toByteArray());
        provider.finish(compressionStream);

        byte[] compressedBytes = compressed.toByteArray();

        addVaryHeader(response, "Accept-Encoding");
        response.setHeader("Content-Encoding", provider.encoding());
        response.setContentLengthLong(compressedBytes.length);

        response.getOutputStream().write(compressedBytes);
        response.getOutputStream().flush();
    }

    private void writeUncompressed(HttpServletResponse response)
            throws IOException {

        if (!isBodylessStatus(response.getStatus())) {
            response.setContentLengthLong(buffer.size());
        }

        response.getOutputStream().write(buffer.toByteArray());
        response.getOutputStream().flush();
    }

    private void addVaryHeader(HttpServletResponse response, String value) {
        String existing = response.getHeader("Vary");

        if (existing == null || existing.isBlank()) {
            response.setHeader("Vary", value);
            return;
        }

        for (String item : existing.split(",")) {
            if (value.equalsIgnoreCase(item.trim())) {
                return;
            }
        }

        response.setHeader("Vary", existing + ", " + value);
    }
}