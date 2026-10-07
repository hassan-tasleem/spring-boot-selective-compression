package com.compression.compression_library.wrapper;

import com.compression.compression_library.interceptor.CompressionInterceptor;
import com.compression.compression_library.negotiation.AcceptEncodingParser;
import com.compression.compression_library.negotiation.CompressionNegotiator;
import com.compression.compression_library.strategy.CompressionProvider;
import com.compression.compression_library.strategy.GzipCompressionProvider;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

class ConditionalCompressionResponseWrapperTest {

    private CompressionNegotiator negotiator;

    @BeforeEach
    void setUp() {

        AcceptEncodingParser parser =
                new AcceptEncodingParser();

        List<CompressionProvider> providers =
                List.of(
                        new GzipCompressionProvider()
                );

        negotiator =
                new CompressionNegotiator(
                        providers,
                        parser
                );
    }

    @Test
    void shouldCompressResponseWhenCompressAnnotationIsPresent() throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        request.addHeader(
                "Accept-Encoding",
                "gzip"
        );

        request.setAttribute(
                CompressionInterceptor.COMPRESS_ATTRIBUTE,
                Boolean.TRUE
        );

        request.setAttribute(
                CompressionInterceptor.MIN_SIZE_ATTRIBUTE,
                10L
        );

        response.setContentType(
                "application/json"
        );

        ConditionalCompressionResponseWrapper wrapper =
                new ConditionalCompressionResponseWrapper(
                        request,
                        response,
                        negotiator
                );

        ServletOutputStream outputStream =
                wrapper.getOutputStream();

        byte[] body =
                "{\"message\":\"Hello World\"}"
                        .getBytes(StandardCharsets.UTF_8);

        outputStream.write(body);

        wrapper.finish();

        assertEquals(
                "gzip",
                response.getHeader("Content-Encoding")
        );

        assertEquals(
                "Accept-Encoding",
                response.getHeader("Vary")
        );

        byte[] compressed =
                response.getContentAsByteArray();

        assertTrue(compressed.length > 0);

        String decompressed =
                decompressGzip(compressed);

        assertEquals(
                "{\"message\":\"Hello World\"}",
                decompressed
        );
    }

    @Test
    void shouldNotCompressWhenCompressAttributeIsMissing()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        request.addHeader(
                "Accept-Encoding",
                "gzip"
        );

        response.setContentType(
                "application/json"
        );

        ConditionalCompressionResponseWrapper wrapper =
                new ConditionalCompressionResponseWrapper(
                        request,
                        response,
                        negotiator
                );

        wrapper.getOutputStream()
                .write(
                        "{\"message\":\"Hello\"}"
                                .getBytes(StandardCharsets.UTF_8)
                );

        wrapper.finish();

        assertNull(
                response.getHeader(
                        "Content-Encoding"
                )
        );

        assertEquals(
                "{\"message\":\"Hello\"}",
                response.getContentAsString()
        );
    }

    @Test
    void shouldNotCompressWhenClientDoesNotAcceptGzip()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        request.addHeader(
                "Accept-Encoding",
                "identity"
        );

        request.setAttribute(
                CompressionInterceptor.COMPRESS_ATTRIBUTE,
                Boolean.TRUE
        );

        request.setAttribute(
                CompressionInterceptor.MIN_SIZE_ATTRIBUTE,
                10L
        );

        response.setContentType(
                "application/json"
        );

        ConditionalCompressionResponseWrapper wrapper =
                new ConditionalCompressionResponseWrapper(
                        request,
                        response,
                        negotiator
                );

        wrapper.getOutputStream()
                .write(
                        "{\"message\":\"Hello World\"}"
                                .getBytes(StandardCharsets.UTF_8)
                );

        wrapper.finish();

        assertNull(
                response.getHeader(
                        "Content-Encoding"
                )
        );

        assertEquals(
                "{\"message\":\"Hello World\"}",
                response.getContentAsString()
        );
    }

    @Test
    void shouldNotCompressSmallResponse()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        request.addHeader(
                "Accept-Encoding",
                "gzip"
        );

        request.setAttribute(
                CompressionInterceptor.COMPRESS_ATTRIBUTE,
                Boolean.TRUE
        );

        request.setAttribute(
                CompressionInterceptor.MIN_SIZE_ATTRIBUTE,
                1024L
        );

        response.setContentType(
                "application/json"
        );

        ConditionalCompressionResponseWrapper wrapper =
                new ConditionalCompressionResponseWrapper(
                        request,
                        response,
                        negotiator
                );

        wrapper.getOutputStream()
                .write(
                        "{\"ok\":true}"
                                .getBytes(StandardCharsets.UTF_8)
                );

        wrapper.finish();

        assertNull(
                response.getHeader(
                        "Content-Encoding"
                )
        );

        assertEquals(
                "{\"ok\":true}",
                response.getContentAsString()
        );
    }

    @Test
    void shouldNotCompressUnsupportedContentType()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        request.addHeader(
                "Accept-Encoding",
                "gzip"
        );

        request.setAttribute(
                CompressionInterceptor.COMPRESS_ATTRIBUTE,
                Boolean.TRUE
        );

        request.setAttribute(
                CompressionInterceptor.MIN_SIZE_ATTRIBUTE,
                10L
        );

        response.setContentType(
                "image/png"
        );

        ConditionalCompressionResponseWrapper wrapper =
                new ConditionalCompressionResponseWrapper(
                        request,
                        response,
                        negotiator
                );

        byte[] body =
                "some image-like data"
                        .getBytes(StandardCharsets.UTF_8);

        wrapper.getOutputStream()
                .write(body);

        wrapper.finish();

        assertNull(
                response.getHeader(
                        "Content-Encoding"
                )
        );

        assertArrayEquals(
                body,
                response.getContentAsByteArray()
        );
    }

    @Test
    void shouldNotCompressNoContentResponse()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        request.addHeader(
                "Accept-Encoding",
                "gzip"
        );

        request.setAttribute(
                CompressionInterceptor.COMPRESS_ATTRIBUTE,
                Boolean.TRUE
        );

        request.setAttribute(
                CompressionInterceptor.MIN_SIZE_ATTRIBUTE,
                10L
        );

        response.setStatus(
                HttpServletResponse.SC_NO_CONTENT
        );

        response.setContentType(
                "application/json"
        );

        ConditionalCompressionResponseWrapper wrapper =
                new ConditionalCompressionResponseWrapper(
                        request,
                        response,
                        negotiator
                );

        wrapper.getOutputStream()
                .write(
                        "this should not be compressed"
                                .getBytes(StandardCharsets.UTF_8)
                );

        wrapper.finish();

        assertNull(
                response.getHeader(
                        "Content-Encoding"
                )
        );
    }

    @Test
    void shouldNotCompressHeadRequest()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.setMethod("HEAD");

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        request.addHeader(
                "Accept-Encoding",
                "gzip"
        );

        request.setAttribute(
                CompressionInterceptor.COMPRESS_ATTRIBUTE,
                Boolean.TRUE
        );

        request.setAttribute(
                CompressionInterceptor.MIN_SIZE_ATTRIBUTE,
                10L
        );

        response.setContentType(
                "application/json"
        );

        ConditionalCompressionResponseWrapper wrapper =
                new ConditionalCompressionResponseWrapper(
                        request,
                        response,
                        negotiator
                );

        wrapper.getOutputStream()
                .write(
                        "large response body"
                                .getBytes(StandardCharsets.UTF_8)
                );

        wrapper.finish();

        assertNull(
                response.getHeader(
                        "Content-Encoding"
                )
        );
    }

    @Test
    void shouldNotCompressAlreadyEncodedResponse()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        request.addHeader(
                "Accept-Encoding",
                "gzip"
        );

        request.setAttribute(
                CompressionInterceptor.COMPRESS_ATTRIBUTE,
                Boolean.TRUE
        );

        request.setAttribute(
                CompressionInterceptor.MIN_SIZE_ATTRIBUTE,
                10L
        );

        response.setContentType(
                "application/json"
        );

        response.setHeader(
                "Content-Encoding",
                "br"
        );

        ConditionalCompressionResponseWrapper wrapper =
                new ConditionalCompressionResponseWrapper(
                        request,
                        response,
                        negotiator
                );

        byte[] body =
                "{\"message\":\"Hello World\"}"
                        .getBytes(StandardCharsets.UTF_8);

        wrapper.getOutputStream()
                .write(body);

        wrapper.finish();

        assertEquals(
                "br",
                response.getHeader(
                        "Content-Encoding"
                )
        );

        assertArrayEquals(
                body,
                response.getContentAsByteArray()
        );
    }

    private String decompressGzip(
            byte[] compressed
    ) throws IOException {

        try (
                GZIPInputStream gzipInputStream =
                        new GZIPInputStream(
                                new java.io.ByteArrayInputStream(
                                        compressed
                                )
                        )
        ) {

            return new String(
                    gzipInputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }
}
