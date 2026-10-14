package com.compression.compression_library.configuration;

import com.compression.compression_library.filter.CompressionFilter;
import com.compression.compression_library.interceptor.CompressionInterceptor;
import com.compression.compression_library.negotiation.AcceptEncodingParser;
import com.compression.compression_library.negotiation.CompressionNegotiator;
import com.compression.compression_library.strategy.BrotliCompressionProvider;
import com.compression.compression_library.strategy.CompressionProvider;
import com.compression.compression_library.strategy.DeflateCompressionProvider;
import com.compression.compression_library.strategy.GzipCompressionProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@AutoConfiguration
@ConditionalOnClass(WebMvcConfigurer.class)
public class WebConfig implements WebMvcConfigurer {

    @Bean
    @ConditionalOnMissingBean
    public AcceptEncodingParser acceptEncodingParser() {
        return new AcceptEncodingParser();
    }

    @Bean
    @ConditionalOnMissingBean(GzipCompressionProvider.class)
    public GzipCompressionProvider gzipCompressionProvider() {
        return new GzipCompressionProvider();
    }

    /*
     * CHANGED: only created when the Brotli native library loads.
     * Otherwise it is skipped instead of crashing the application.
     */
    @Bean
    @Conditional(BrotliAvailableCondition.class)
    @ConditionalOnMissingBean(BrotliCompressionProvider.class)
    public BrotliCompressionProvider brotliCompressionProvider() {
        return new BrotliCompressionProvider();
    }

    @Bean
    @ConditionalOnMissingBean(DeflateCompressionProvider.class)
    public DeflateCompressionProvider deflateCompressionProvider() {
        return new DeflateCompressionProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    public CompressionNegotiator compressionNegotiator(
            List<CompressionProvider> providers,
            AcceptEncodingParser parser
    ) {
        return new CompressionNegotiator(providers, parser);
    }

    @Bean
    @ConditionalOnMissingBean
    public CompressionInterceptor compressionInterceptor() {
        return new CompressionInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(compressionInterceptor());
    }

    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<CompressionFilter> compressionFilter(
            CompressionNegotiator negotiator
    ) {
        FilterRegistrationBean<CompressionFilter> registration =
                new FilterRegistrationBean<>();

        registration.setFilter(new CompressionFilter(negotiator));
        registration.addUrlPatterns("/*");
        registration.setOrder(Integer.MIN_VALUE);

        return registration;
    }
}