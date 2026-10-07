package com.compression.compression_library.negotiation;

import com.compression.compression_library.strategy.CompressionProvider;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class CompressionNegotiator {

    private final List<CompressionProvider> providers;

    private final AcceptEncodingParser parser;

    public CompressionNegotiator(
            List<CompressionProvider> providers,
            AcceptEncodingParser parser
    ) {

        this.providers =
                providers.stream()
                        .sorted(
                                Comparator.comparingInt(
                                        CompressionProvider::serverPriority
                                )
                        )
                        .toList();

        this.parser = parser;
    }

    public CompressionProvider negotiate(
            HttpServletRequest request
    ) {

        List<EncodingPreference> preferences =
                parser.parse(request);

        if (preferences.isEmpty()) {
            return null;
        }

        /*
         * Explicit identity preference.
         */
        EncodingPreference identity =
                findPreference(
                        preferences,
                        "identity"
                );

        /*
         * Wildcard preference.
         */
        EncodingPreference wildcard =
                findPreference(
                        preferences,
                        "*"
                );

        List<Candidate> candidates =
                new ArrayList<>();

        for (CompressionProvider provider : providers) {

            String encoding =
                    provider.encoding()
                            .toLowerCase(Locale.ROOT);

            /*
             * Explicit encoding preference
             * always wins over wildcard.
             */
            EncodingPreference explicit =
                    findPreference(
                            preferences,
                            encoding
                    );

            double quality;

            if (explicit != null) {

                quality =
                        explicit.quality();

            } else if (wildcard != null) {

                /*
                 * If wildcard is explicitly q=0,
                 * the encoding is not acceptable.
                 */
                quality =
                        wildcard.quality();

            } else {

                /*
                 * Encoding wasn't mentioned.
                 *
                 * It is not automatically acceptable
                 * when the header contains explicit
                 * encoding choices.
                 */
                continue;
            }

            /*
             * q=0 means "not acceptable".
             */
            if (quality <= 0.0) {
                continue;
            }

            candidates.add(
                    new Candidate(
                            provider,
                            quality
                    )
            );
        }

        /*
         * Highest client quality wins.
         *
         * If qualities are equal,
         * server priority wins.
         */
        candidates.sort(
                Comparator
                        .comparingDouble(
                                Candidate::quality
                        )
                        .reversed()
                        .thenComparingInt(
                                candidate ->
                                        candidate
                                                .provider()
                                                .serverPriority()
                        )
        );

        /*
         * Return best compression algorithm.
         */
        if (!candidates.isEmpty()) {

            return candidates
                    .get(0)
                    .provider();
        }

        /*
         * No compression algorithm accepted.
         *
         * Returning null means:
         *
         * identity
         */
        return null;
    }

    private EncodingPreference findPreference(
            List<EncodingPreference> preferences,
            String encoding
    ) {

        for (EncodingPreference preference :
                preferences) {

            if (encoding.equalsIgnoreCase(
                    preference.encoding()
            )) {

                return preference;
            }
        }

        return null;
    }

    private record Candidate(
            CompressionProvider provider,
            double quality
    ) {
    }
}
