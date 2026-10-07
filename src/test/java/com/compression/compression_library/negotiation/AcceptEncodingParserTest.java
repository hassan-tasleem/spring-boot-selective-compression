package com.compression.compression_library.negotiation;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AcceptEncodingParserTest {

    private final AcceptEncodingParser parser =
            new AcceptEncodingParser();

    @Test
    void shouldParseGzip() {

        HttpServletRequest request =
                mock(HttpServletRequest.class);

        when(request.getHeader("Accept-Encoding"))
                .thenReturn("gzip");

        List<EncodingPreference> result =
                parser.parse(request);

        assertEquals(1, result.size());

        assertEquals(
                "gzip",
                result.get(0).encoding()
        );

        assertEquals(
                1.0,
                result.get(0).quality()
        );
    }

    @Test
    void shouldParseMultipleEncodings() {

        HttpServletRequest request =
                mock(HttpServletRequest.class);

        when(request.getHeader("Accept-Encoding"))
                .thenReturn("br, gzip");

        List<EncodingPreference> result =
                parser.parse(request);

        assertEquals(2, result.size());

        assertEquals(
                "br",
                result.get(0).encoding()
        );

        assertEquals(
                "gzip",
                result.get(1).encoding()
        );
    }

    @Test
    void shouldParseQualityValues() {

        HttpServletRequest request =
                mock(HttpServletRequest.class);

        when(request.getHeader("Accept-Encoding"))
                .thenReturn(
                        "br;q=1.0, gzip;q=0.5"
                );

        List<EncodingPreference> result =
                parser.parse(request);

        assertEquals(
                1.0,
                result.get(0).quality()
        );

        assertEquals(
                0.5,
                result.get(1).quality()
        );
    }

    @Test
    void shouldParseZeroQuality() {

        HttpServletRequest request =
                mock(HttpServletRequest.class);

        when(request.getHeader("Accept-Encoding"))
                .thenReturn("gzip;q=0");

        List<EncodingPreference> result =
                parser.parse(request);

        assertEquals(
                0.0,
                result.get(0).quality()
        );
    }

    @Test
    void shouldParseWildcard() {

        HttpServletRequest request =
                mock(HttpServletRequest.class);

        when(request.getHeader("Accept-Encoding"))
                .thenReturn("*");

        List<EncodingPreference> result =
                parser.parse(request);

        assertEquals("*", result.get(0).encoding());

        assertEquals(
                1.0,
                result.get(0).quality()
        );
    }

    @Test
    void shouldHandleMissingHeader() {

        HttpServletRequest request =
                mock(HttpServletRequest.class);

        when(request.getHeader("Accept-Encoding"))
                .thenReturn(null);

        List<EncodingPreference> result =
                parser.parse(request);

        assertEquals(1, result.size());

        assertEquals(
                "*",
                result.get(0).encoding()
        );

        assertEquals(
                1.0,
                result.get(0).quality()
        );
    }

    @Test
    void shouldHandleEmptyHeader() {

        HttpServletRequest request =
                mock(HttpServletRequest.class);

        when(request.getHeader("Accept-Encoding"))
                .thenReturn("");

        List<EncodingPreference> result =
                parser.parse(request);

        assertEquals(1, result.size());

        assertEquals(
                "identity",
                result.get(0).encoding()
        );
    }
}
