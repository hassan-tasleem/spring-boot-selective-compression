package com.compression.compression_library.interceptor;

import com.compression.compression_library.annotation.Compress;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class CompressionInterceptor
        implements HandlerInterceptor {

    public static final String COMPRESS_ATTRIBUTE =
            CompressionInterceptor.class.getName()
                    + ".COMPRESS";

    public static final String MIN_SIZE_ATTRIBUTE =
            CompressionInterceptor.class.getName()
                    + ".MIN_SIZE";

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {

        /*
         * Static resources / other handlers are ignored.
         */
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        Compress classAnnotation =
                handlerMethod
                        .getBeanType()
                        .getAnnotation(Compress.class);

        Compress methodAnnotation =
                handlerMethod
                        .getMethodAnnotation(Compress.class);

        /*
         * Class and method annotation together
         * are deliberately forbidden.
         */
        if (classAnnotation != null
                && methodAnnotation != null) {

            throw new IllegalStateException(
                    "@Compress cannot be declared on both "
                            + "controller class and endpoint method. "
                            + "Class: "
                            + handlerMethod
                            .getBeanType()
                            .getName()
                            + ", Method: "
                            + handlerMethod
                            .getMethod()
                            .getName()
            );
        }

        /*
         * Method-level annotation takes precedence
         * when present.
         */
        Compress selectedAnnotation =
                methodAnnotation != null
                        ? methodAnnotation
                        : classAnnotation;

        if (selectedAnnotation != null) {

            request.setAttribute(
                    COMPRESS_ATTRIBUTE,
                    Boolean.TRUE
            );

            request.setAttribute(
                    MIN_SIZE_ATTRIBUTE,
                    selectedAnnotation.minResponseSize()
            );
        }

        return true;
    }
}
