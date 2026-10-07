package com.compression.compression_library.negotiation;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AcceptEncodingParser {

    public List<EncodingPreference> parse(HttpServletRequest request) {

        String header = request.getHeader("Accept-Encoding");

        /*
         * No Accept-Encoding header:
         *
         * According to HTTP semantics, the absence of the
         * header means the client has not explicitly rejected
         * content codings.
         */
        if (header == null) {
            return List.of(
                    new EncodingPreference("*", 1.0, 0)
            );
        }

        /*
         * Explicitly empty header.
         */
        if (header.isBlank()) {
            return List.of(
                    new EncodingPreference("identity", 1.0, 0)
            );
        }

        List<EncodingPreference> result = new ArrayList<>();

        String[] entries = header.split(",");

        int order = 0;

        for (String entry : entries) {

            entry = entry.trim();

            if (entry.isEmpty()) {
                continue;
            }

            String[] parts = entry.split(";");

            String encoding = parts[0]
                    .trim()
                    .toLowerCase(Locale.ROOT);

            double quality = 1.0;

            for (int i = 1; i < parts.length; i++) {

                String parameter = parts[i].trim();

                int equalsIndex = parameter.indexOf('=');

                if (equalsIndex < 0) {
                    continue;
                }

                String name = parameter
                        .substring(0, equalsIndex)
                        .trim()
                        .toLowerCase(Locale.ROOT);

                String value = parameter
                        .substring(equalsIndex + 1)
                        .trim();

                if ("q".equals(name)) {

                    try {
                        quality = Double.parseDouble(value);
                    } catch (NumberFormatException exception) {
                        quality = 0.0;
                    }
                }
            }

            /*
             * q must be between 0 and 1.
             */
            if (quality < 0.0 || quality > 1.0) {
                quality = 0.0;
            }

            result.add(
                    new EncodingPreference(
                            encoding,
                            quality,
                            order++
                    )
            );
        }

        return result;
    }
}
