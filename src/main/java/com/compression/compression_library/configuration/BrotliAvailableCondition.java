package com.compression.compression_library.configuration;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * True only when the Brotli native library can really be loaded.
 * If it can't, the Brotli provider is skipped and GZIP/Deflate still work.
 */
public class BrotliAvailableCondition implements Condition {

    private static final Logger log =
            LoggerFactory.getLogger(BrotliAvailableCondition.class);

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        try {
            if (Brotli4jLoader.isAvailable()) {
                return true;
            }
            log.warn("Brotli native library is not available on this platform. "
                    + "Brotli compression is disabled; GZIP and Deflate still work. "
                    + "Add the matching com.aayushatharva.brotli4j:native-<platform> "
                    + "dependency to enable it.");
            return false;
        } catch (Throwable error) {
            log.warn("Brotli could not be initialised ({}). "
                            + "Brotli compression is disabled; GZIP and Deflate still work.",
                    error.toString());
            return false;
        }
    }
}