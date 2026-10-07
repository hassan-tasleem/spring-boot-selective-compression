package com.compression.compression_library.negotiation;

import com.compression.compression_library.strategy.CompressionProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompressionNegotiatorTest {

    private CompressionNegotiator negotiator;

    private CompressionProvider brotli;

    private CompressionProvider gzip;

    @BeforeEach
    void setUp() {

        brotli = mock(CompressionProvider.class);
        gzip = mock(CompressionProvider.class);

        when(brotli.encoding())
                .thenReturn("br");

        when(brotli.serverPriority())
                .thenReturn(1);

        when(gzip.encoding())
                .thenReturn("gzip");

        when(gzip.serverPriority())
                .thenReturn(2);

        AcceptEncodingParser parser =
                new AcceptEncodingParser();

        negotiator =
                new CompressionNegotiator(
                        List.of(
                                brotli,
                                gzip
                        ),
                        parser
                );
    }

    @Test
    void shouldSelectGzip() {

        HttpServletRequest request =
                requestWithHeader("gzip");

        CompressionProvider result =
                negotiator.negotiate(request);

        assertSame(
                gzip,
                result
        );
    }

    @Test
    void shouldSelectBrotli() {

        HttpServletRequest request =
                requestWithHeader("br");

        CompressionProvider result =
                negotiator.negotiate(request);

        assertSame(
                brotli,
                result
        );
    }

    @Test
    void shouldSelectBrotliWhenBothAreAccepted() {

        HttpServletRequest request =
                requestWithHeader(
                        "br, gzip"
                );

        CompressionProvider result =
                negotiator.negotiate(request);

        /*
         * Both have q=1.0.
         *
         * Brotli has server priority 1.
         * Gzip has server priority 2.
         *
         * Lower priority number wins.
         */
        assertSame(
                brotli,
                result
        );
    }

    @Test
    void shouldPreferHigherQuality() {

        HttpServletRequest request =
                requestWithHeader(
                        "br;q=0.5, gzip;q=1.0"
                );

        CompressionProvider result =
                negotiator.negotiate(request);

        assertSame(
                gzip,
                result
        );
    }

    @Test
    void shouldPreferBrotliWhenItsQualityIsHigher() {

        HttpServletRequest request =
                requestWithHeader(
                        "br;q=1.0, gzip;q=0.5"
                );

        CompressionProvider result =
                negotiator.negotiate(request);

        assertSame(
                brotli,
                result
        );
    }

    @Test
    void shouldRejectGzipWhenQualityIsZero() {

        HttpServletRequest request =
                requestWithHeader(
                        "gzip;q=0"
                );

        CompressionProvider result =
                negotiator.negotiate(request);

        assertNull(result);
    }

    @Test
    void shouldUseWildcard() {

        HttpServletRequest request =
                requestWithHeader("*");

        CompressionProvider result =
                negotiator.negotiate(request);

        /*
         * Both algorithms are accepted by *.
         *
         * Brotli wins because it has
         * the better server priority.
         */
        assertSame(
                brotli,
                result
        );
    }

    @Test
    void shouldRejectAllCompressionWhenWildcardQualityIsZero() {

        HttpServletRequest request =
                requestWithHeader("*;q=0");

        CompressionProvider result =
                negotiator.negotiate(request);

        assertNull(result);
    }

    @Test
    void explicitEncodingShouldOverrideWildcard() {

        HttpServletRequest request =
                requestWithHeader(
                        "*;q=1.0, gzip;q=0"
                );

        CompressionProvider result =
                negotiator.negotiate(request);

        /*
         * Wildcard says everything is accepted.
         *
         * But gzip explicitly says q=0.
         *
         * Therefore gzip must NOT be selected.
         *
         * Brotli can still use wildcard q=1.0.
         */
        assertSame(
                brotli,
                result
        );
    }

    @Test
    void shouldReturnNullWhenOnlyIdentityIsAccepted() {

        HttpServletRequest request =
                requestWithHeader(
                        "identity"
                );

        CompressionProvider result =
                negotiator.negotiate(request);

        assertNull(result);
    }

    @Test
    void shouldUseWildcardWhenNoExplicitEncodingExists() {

        HttpServletRequest request =
                requestWithHeader(
                        "gzip;q=0.5, *;q=1.0"
                );

        CompressionProvider result =
                negotiator.negotiate(request);

        /*
         * gzip explicitly has q=0.5.
         *
         * Brotli is not explicitly mentioned,
         * therefore it uses wildcard q=1.0.
         */
        assertSame(
                brotli,
                result
        );
    }

    @Test
    void shouldTreatMissingHeaderAsWildcard() {

        HttpServletRequest request =
                mock(HttpServletRequest.class);

        when(request.getHeader("Accept-Encoding"))
                .thenReturn(null);

        CompressionProvider result =
                negotiator.negotiate(request);

        /*
         * Missing Accept-Encoding is treated
         * by our parser as "*;q=1.0".
         */
        assertSame(
                brotli,
                result
        );
    }

    @Test
    void shouldReturnNullForEmptyHeader() {

        HttpServletRequest request =
                requestWithHeader("");

        CompressionProvider result =
                negotiator.negotiate(request);

        assertNull(result);
    }

    @Test
    void shouldTreatEncodingNamesCaseInsensitively() {

        HttpServletRequest request =
                requestWithHeader(
                        "BR"
                );

        CompressionProvider result =
                negotiator.negotiate(request);

        assertSame(
                brotli,
                result
        );
    }

    @Test
    void shouldIgnoreUnsupportedEncoding() {

        HttpServletRequest request =
                requestWithHeader(
                        "deflate"
                );

        CompressionProvider result =
                negotiator.negotiate(request);

        assertNull(result);
    }

    @Test
    void shouldIgnoreUnsupportedEncodingAndSelectSupportedOne() {

        HttpServletRequest request =
                requestWithHeader(
                        "deflate, gzip"
                );

        CompressionProvider result =
                negotiator.negotiate(request);

        assertSame(
                gzip,
                result
        );
    }

    private HttpServletRequest requestWithHeader(
            String value
    ) {

        HttpServletRequest request =
                mock(HttpServletRequest.class);

        when(request.getHeader("Accept-Encoding"))
                .thenReturn(value);

        return request;
    }
}
