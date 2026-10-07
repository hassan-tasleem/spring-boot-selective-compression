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

    private CompressionProvider compressionProvider;

    private OutputStream compressionOutputStream;

    private ServletOutputStream outputStream;

    private PrintWriter writer;

    /*
     * Buffers the response only until
     * minResponseSize is reached.
     */
    private ByteArrayOutputStream buffer;

    private boolean compressionStarted;

    private boolean compressionDisabled;

    private boolean finished;

    /*
     * Content types suitable for compression.
     */
    private static final Set<String>
            COMPRESSIBLE_CONTENT_TYPES = Set.of(
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
            "text/csv",
            "text/event-stream"
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
     * 
     * Compression decision
     * 
     */

    private boolean compressionRequested() {

        boolean endpointRequiresCompression =
                Boolean.TRUE.equals(
                        request.getAttribute(
                                CompressionInterceptor
                                        .COMPRESS_ATTRIBUTE
                        )
                );

        if (!endpointRequiresCompression) {
            return false;
        }

        /*
         * HEAD has no response body.
         */
        if ("HEAD".equalsIgnoreCase(
                request.getMethod()
        )) {
            return false;
        }

        HttpServletResponse response =
                (HttpServletResponse) getResponse();

        int status = response.getStatus();

        /*
         * 204 and 304 have no response body.
         */
        if (status ==
                HttpServletResponse.SC_NO_CONTENT
                ||
                status ==
                        HttpServletResponse.SC_NOT_MODIFIED) {

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
     * 
     * Minimum response size
     * 
     */

    private long getMinResponseSize() {

        Object minSize =
                request.getAttribute(
                        CompressionInterceptor
                                .MIN_SIZE_ATTRIBUTE
                );

        if (minSize instanceof Number number) {

            return Math.max(
                    0,
                    number.longValue()
            );
        }

        return 1024;
    }

    /*
     * 
     * Content-Type
     * 
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

        if (mediaType.startsWith("text/")) {
            return true;
        }

        return COMPRESSIBLE_CONTENT_TYPES
                .contains(mediaType);
    }

    /*
     * 
     * Already encoded
     * 
     */

    private boolean alreadyEncoded() {

        HttpServletResponse response =
                (HttpServletResponse)
                        getResponse();

        String contentEncoding =
                response.getHeader(
                        "Content-Encoding"
                );

        return contentEncoding != null
                && !contentEncoding.isBlank()
                && !"identity".equalsIgnoreCase(
                contentEncoding
        );
    }

    /*
     * 
     * OutputStream
     * 
     */

    @Override
    public ServletOutputStream getOutputStream()
            throws IOException {

        if (writer != null) {

            throw new IllegalStateException(
                    "getWriter() has already been called"
            );
        }

        if (!compressionRequested()) {
            return super.getOutputStream();
        }

        if (compressionDisabled) {
            return super.getOutputStream();
        }

        if (outputStream == null) {

            outputStream =
                    new ServletOutputStream() {

                        @Override
                        public void write(int b)
                                throws IOException {

                            writeBytes(
                                    new byte[]{
                                            (byte) b
                                    },
                                    0,
                                    1
                            );
                        }

                        @Override
                        public void write(
                                byte[] b,
                                int off,
                                int len
                        ) throws IOException {

                            writeBytes(
                                    b,
                                    off,
                                    len
                            );
                        }

                        @Override
                        public void flush()
                                throws IOException {

                            if (compressionStarted) {

                                compressionOutputStream
                                        .flush();

                            } else if (
                                    compressionDisabled
                            ) {

                                getResponse()
                                        .getOutputStream()
                                        .flush();
                            }
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
                             * Synchronous servlet processing.
                             */
                        }
                    };
        }

        return outputStream;
    }

    /*
     * 
     * Write
     * 
     */

    private void writeBytes(
            byte[] bytes,
            int offset,
            int length
    ) throws IOException {

        if (length == 0) {
            return;
        }

        /*
         * Compression disabled.
         */
        if (compressionDisabled) {

            getResponse()
                    .getOutputStream()
                    .write(
                            bytes,
                            offset,
                            length
                    );

            return;
        }

        /*
         * Compression already started.
         */
        if (compressionStarted) {

            compressionOutputStream.write(
                    bytes,
                    offset,
                    length
            );

            return;
        }

        /*
         * Create buffer.
         */
        if (buffer == null) {

            buffer =
                    new ByteArrayOutputStream();
        }

        /*
         * Buffer response bytes.
         */
        buffer.write(
                bytes,
                offset,
                length
        );

        /*
         * Start compression when
         * minimum size is reached.
         */
        if (buffer.size() >=
                getMinResponseSize()) {

            startCompression();

            if (compressionDisabled) {
                return;
            }

            /*
             * Send buffered data into
             * compression stream.
             */
            buffer.writeTo(
                    compressionOutputStream
            );

            buffer.reset();
            buffer = null;
        }
    }

    /*
     * 
     * Writer
     * 
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

            writer =
                    new PrintWriter(
                            getOutputStream(),
                            false,
                            StandardCharsets.UTF_8
                    );
        }

        return writer;
    }

    /*
     * 
     * Start compression
     * 
     */

    private void startCompression()
            throws IOException {

        if (compressionStarted
                || compressionDisabled) {

            return;
        }

        /*
         * Existing Content-Encoding means
         * another component already encoded it.
         */
        if (alreadyEncoded()) {

            disableCompression();

            return;
        }

        /*
         * Check Content-Type.
         */
        if (!isCompressibleContentType()) {

            disableCompression();

            return;
        }

        /*
         * Negotiate encoding.
         */
        CompressionProvider provider =
                compressionNegotiator.negotiate(
                        request
                );

        /*
         * No acceptable compression.
         */
        if (provider == null) {

            disableCompression();

            return;
        }

        HttpServletResponse response =
                (HttpServletResponse)
                        getResponse();

        /*
         * The original Content-Length describes
         * the uncompressed response.
         *
         * It must be removed before compression.
         */
        response.setHeader(
                "Content-Length",
                null
        );

        /*
         * Set Vary before writing compressed bytes.
         */
        response.setHeader(
                "Vary",
                "Accept-Encoding"
        );

        /*
         * Tell client which encoding is used.
         */
        response.setHeader(
                "Content-Encoding",
                provider.encoding()
        );

        /*
         * Store provider.
         */
        compressionProvider = provider;

        /*
         * Create compression stream.
         */
        compressionOutputStream =
                provider.createOutputStream(
                        response
                );

        compressionStarted = true;
    }

    /*
     * 
     * Disable compression
     * 
     */

    private void disableCompression()
            throws IOException {

        compressionDisabled = true;

        if (buffer != null) {

            buffer.writeTo(
                    getResponse()
                            .getOutputStream()
            );

            buffer.reset();
            buffer = null;
        }
    }

    /*
     * 
     * Finish
     * 
     */

    public void finish()
            throws IOException {

        if (finished) {
            return;
        }

        finished = true;

        /*
         * Flush PrintWriter first.
         */
        if (writer != null) {
            writer.flush();
        }

        /*
         * Compression never started.
         */
        if (!compressionStarted) {

            /*
             * Already sent because compression
             * was disabled.
             */
            if (compressionDisabled) {
                return;
            }

            /*
             * Response remained below
             * minimum compression size.
             */
            if (buffer != null) {

                buffer.writeTo(
                        getResponse()
                                .getOutputStream()
                );

                buffer.reset();
                buffer = null;
            }

            return;
        }

        /*
         * Finalize compression.
         */
        compressionProvider.finish(
                compressionOutputStream
        );
    }

    /*
     * 
     * Content-Length
     * 
     */

    @Override
    public void setContentLength(int len) {

        if (!compressionStarted) {
            super.setContentLength(len);
        }
    }

    @Override
    public void setContentLengthLong(long len) {

        if (!compressionStarted) {
            super.setContentLengthLong(len);
        }
    }

    /*
     * 
     * Headers
     * 
     */

    @Override
    public void setHeader(
            String name,
            String value
    ) {

        if ("Content-Length".equalsIgnoreCase(name)
                && compressionStarted) {

            return;
        }

        super.setHeader(
                name,
                value
        );
    }

    @Override
    public void addHeader(
            String name,
            String value
    ) {

        if ("Content-Length".equalsIgnoreCase(name)
                && compressionStarted) {

            return;
        }

        super.addHeader(
                name,
                value
        );
    }
}
