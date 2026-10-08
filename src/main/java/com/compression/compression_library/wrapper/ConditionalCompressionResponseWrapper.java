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
     * We always buffer the response.
     *
     * This avoids modifying the real servlet output stream
     * before we know whether compression should happen.
     */
    private final ByteArrayOutputStream buffer =
            new ByteArrayOutputStream();

    private ServletOutputStream outputStream;
    private PrintWriter writer;

    private boolean finished;

    /*
     * Content types suitable for compression.
     */
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
        this.compressionNegotiator =
                compressionNegotiator;
    }

    /*
     * ---------------------------------------------------------
     * Compression requested?
     * ---------------------------------------------------------
     */

    private boolean compressionRequested() {

        boolean requested =
                Boolean.TRUE.equals(
                        request.getAttribute(
                                CompressionInterceptor
                                        .COMPRESS_ATTRIBUTE
                        )
                );

        if (!requested) {
            return false;
        }

        /*
         * HEAD has no response body.
         */
        if ("HEAD".equalsIgnoreCase(
                request.getMethod())) {

            return false;
        }

        HttpServletResponse response = (HttpServletResponse) getResponse();
        int status = response.getStatus();

        /*
         * Responses without bodies.
         */
        if (status == HttpServletResponse.SC_NO_CONTENT
                || status == HttpServletResponse.SC_NOT_MODIFIED) {

            return false;
        }

        /*
         * Informational responses.
         */
        if (status >= 100 && status < 200) {
            return false;
        }

        return true;
    }

    /*
     * ---------------------------------------------------------
     * Minimum response size
     * ---------------------------------------------------------
     */

    private long getMinResponseSize() {

        Object value =
                request.getAttribute(
                        CompressionInterceptor
                                .MIN_SIZE_ATTRIBUTE
                );

        if (value instanceof Number number) {

            return Math.max(
                    0,
                    number.longValue()
            );
        }

        return 1024;
    }

    /*
     * ---------------------------------------------------------
     * Content type
     * ---------------------------------------------------------
     */

    private boolean isCompressibleContentType() {

        String contentType =
                getResponse().getContentType();

        if (contentType == null
                || contentType.isBlank()) {

            return false;
        }

        String mediaType =
                contentType
                        .toLowerCase(Locale.ROOT)
                        .split(";")[0]
                        .trim();

        /*
         * All text/* types are compressible.
         */
        if (mediaType.startsWith("text/")) {
            return true;
        }

        return COMPRESSIBLE_CONTENT_TYPES
                .contains(mediaType);
    }

    /*
     * ---------------------------------------------------------
     * Already encoded?
     * ---------------------------------------------------------
     */

    private boolean alreadyEncoded() {

        HttpServletResponse response = (HttpServletResponse) getResponse();
        String contentEncoding = response.getHeader("Content-Encoding");

        return contentEncoding != null
                && !contentEncoding.isBlank()
                && !"identity".equalsIgnoreCase(
                contentEncoding
        );
    }

    /*
     * ---------------------------------------------------------
     * OutputStream
     * ---------------------------------------------------------
     */

    @Override
    public ServletOutputStream getOutputStream()
            throws IOException {

        if (writer != null) {

            throw new IllegalStateException(
                    "getWriter() has already been called"
            );
        }

        if (outputStream == null) {

            outputStream =
                    new ServletOutputStream() {

                        @Override
                        public void write(int b)
                                throws IOException {

                            buffer.write(b);
                        }

                        @Override
                        public void write(
                                byte[] b,
                                int off,
                                int len
                        ) throws IOException {

                            buffer.write(
                                    b,
                                    off,
                                    len
                            );
                        }

                        @Override
                        public void flush()
                                throws IOException {

                            /*
                             * Do NOT flush the real response.
                             *
                             * The response is intentionally
                             * buffered until finish().
                             */
                        }

                        @Override
                        public boolean isReady() {
                            return true;
                        }

                        @Override
                        public void setWriteListener(
                                WriteListener listener
                        ) {
                            /*
                             * Synchronous processing.
                             */
                        }
                    };
        }

        return outputStream;
    }

    /*
     * ---------------------------------------------------------
     * Writer
     * ---------------------------------------------------------
     */

    @Override
    public PrintWriter getWriter()
            throws IOException {

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

    /*
     * ---------------------------------------------------------
     * Finish response
     * ---------------------------------------------------------
     */

    public void finish()
            throws IOException {

        if (finished) {
            return;
        }

        finished = true;

        /*
         * Make sure all writer data is inside our buffer.
         */
        if (writer != null) {
            writer.flush();
        }

        /*
         * If compression was not requested,
         * send the original response.
         */
        if (!compressionRequested()) {

            writeUncompressed();

            return;
        }

        /*
         * Do not compress unsupported content.
         */
        if (!isCompressibleContentType()) {

            writeUncompressed();

            return;
        }

        /*
         * Do not compress an already encoded response.
         */
        if (alreadyEncoded()) {

            writeUncompressed();

            return;
        }

        /*
         * Check minimum response size.
         */
        if (buffer.size() < getMinResponseSize()) {

            writeUncompressed();

            return;
        }

        /*
         * Select Brotli/GZIP according to Accept-Encoding.
         */
        CompressionProvider provider =
                compressionNegotiator.negotiate(
                        request
                );

        /*
         * Client doesn't accept compression.
         */
        if (provider == null) {

            writeUncompressed();

            return;
        }

        /*
         * -----------------------------------------------------
         * Compress into memory first.
         * -----------------------------------------------------
         */

        ByteArrayOutputStream compressed =
                new ByteArrayOutputStream();

        OutputStream compressionStream =
                provider.createOutputStream(
                        compressed
                );

        compressionStream.write(
                buffer.toByteArray()
        );

        provider.finish(
                compressionStream
        );

        byte[] compressedBytes =
                compressed.toByteArray();

        /*
         * -----------------------------------------------------
         * Set compression headers.
         * -----------------------------------------------------
         */

        HttpServletResponse response =
                (HttpServletResponse) getResponse();

        /*
         * Remove original Content-Length.
         */
        response.setHeader(
                "Content-Length",
                null
        );

        /*
         * Tell caches that representation varies
         * depending on Accept-Encoding.
         */
        addVaryHeader(
                response,
                "Accept-Encoding"
        );

        /*
         * Tell client which compression was used.
         */
        response.setHeader(
                "Content-Encoding",
                provider.encoding()
        );

        /*
         * The compressed response has a NEW length.
         */
        response.setContentLengthLong(
                compressedBytes.length
        );

        /*
         * Finally send compressed bytes.
         */
        response.getOutputStream()
                .write(compressedBytes);

        response.getOutputStream()
                .flush();
    }

    /*
     * ---------------------------------------------------------
     * Send uncompressed response
     * ---------------------------------------------------------
     */

    private void writeUncompressed()
            throws IOException {

        HttpServletResponse response =
                (HttpServletResponse) getResponse();

        /*
         * If nobody already specified Content-Length,
         * we can safely provide the buffered size.
         */
        if (!response.containsHeader(
                "Content-Length")) {

            response.setContentLengthLong(
                    buffer.size()
            );
        }

        response.getOutputStream()
                .write(
                        buffer.toByteArray()
                );

        response.getOutputStream()
                .flush();
    }

    /*
     * ---------------------------------------------------------
     * Vary header
     * ---------------------------------------------------------
     */

    private void addVaryHeader(
            HttpServletResponse response,
            String value
    ) {

        String existing =
                response.getHeader("Vary");

        if (existing == null
                || existing.isBlank()) {

            response.setHeader(
                    "Vary",
                    value
            );

            return;
        }

        /*
         * Don't add duplicate value.
         */
        for (String item :
                existing.split(",")) {

            if (value.equalsIgnoreCase(
                    item.trim())) {

                return;
            }
        }

        response.setHeader(
                "Vary",
                existing + ", " + value
        );
    }

    /*
     * ---------------------------------------------------------
     * Content-Length
     * ---------------------------------------------------------
     */

    @Override
    public void setContentLength(int len) {

        /*
         * The controller's original length refers
         * to the uncompressed body.
         *
         * We don't want that value to survive
         * compression.
         */
        super.setContentLength(len);
    }

    @Override
    public void setContentLengthLong(long len) {

        super.setContentLengthLong(len);
    }
}