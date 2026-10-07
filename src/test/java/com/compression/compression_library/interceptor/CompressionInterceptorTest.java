package com.compression.compression_library.interceptor;

import com.compression.compression_library.annotation.Compress;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CompressionInterceptorTest {

    private CompressionInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new CompressionInterceptor();
    }

    @Test
    void shouldEnableCompressionForClassAnnotation()
            throws Exception {

        HandlerMethod handlerMethod =
                handlerMethod(
                        ClassAnnotatedController.class,
                        "compressed"
                );

        HttpServletRequest request =
                mock(HttpServletRequest.class);

        HttpServletResponse response =
                mock(HttpServletResponse.class);

        boolean result =
                interceptor.preHandle(
                        request,
                        response,
                        handlerMethod
                );

        assertTrue(result);

        verify(request).setAttribute(
                CompressionInterceptor.COMPRESS_ATTRIBUTE,
                Boolean.TRUE
        );

        verify(request).setAttribute(
                CompressionInterceptor.MIN_SIZE_ATTRIBUTE,
                4096L
        );
    }

    @Test
    void shouldEnableCompressionForMethodAnnotation()
            throws Exception {

        HandlerMethod handlerMethod =
                handlerMethod(
                        CompressedController.class,
                        "compressed"
                );

        HttpServletRequest request =
                mock(HttpServletRequest.class);

        HttpServletResponse response =
                mock(HttpServletResponse.class);

        boolean result =
                interceptor.preHandle(
                        request,
                        response,
                        handlerMethod
                );

        assertTrue(result);

        verify(request).setAttribute(
                CompressionInterceptor.COMPRESS_ATTRIBUTE,
                Boolean.TRUE
        );

        verify(request).setAttribute(
                CompressionInterceptor.MIN_SIZE_ATTRIBUTE,
                2048L
        );
    }

    @Test
    void shouldNotEnableCompressionWithoutAnnotation()
            throws Exception {

        HandlerMethod handlerMethod =
                handlerMethod(
                        NormalController.class,
                        "normal"
                );

        HttpServletRequest request =
                mock(HttpServletRequest.class);

        HttpServletResponse response =
                mock(HttpServletResponse.class);

        boolean result =
                interceptor.preHandle(
                        request,
                        response,
                        handlerMethod
                );

        assertTrue(result);

        org.mockito.Mockito.verifyNoInteractions(request);
    }

    @Test
    void shouldRejectClassAndMethodAnnotationsTogether()
            throws Exception {

        HandlerMethod handlerMethod =
                handlerMethod(
                        BothAnnotatedController.class,
                        "compressed"
                );

        HttpServletRequest request =
                mock(HttpServletRequest.class);

        HttpServletResponse response =
                mock(HttpServletResponse.class);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> interceptor.preHandle(
                                request,
                                response,
                                handlerMethod
                        )
                );

        assertTrue(
                exception.getMessage()
                        .contains(
                                "@Compress cannot be declared on both"
                        )
        );

        assertTrue(
                exception.getMessage()
                        .contains(
                                "BothAnnotatedController"
                        )
        );

        assertTrue(
                exception.getMessage()
                        .contains(
                                "compressed"
                        )
        );
    }

    @Test
    void shouldIgnoreNonHandlerMethod()
            throws Exception {

        HttpServletRequest request =
                mock(HttpServletRequest.class);

        HttpServletResponse response =
                mock(HttpServletResponse.class);

        Object handler = new Object();

        boolean result =
                interceptor.preHandle(
                        request,
                        response,
                        handler
                );

        assertTrue(result);

        org.mockito.Mockito.verifyNoInteractions(request);
    }

    private HandlerMethod handlerMethod(
            Class<?> controllerClass,
            String methodName
    ) throws Exception {

        Object controller =
                controllerClass
                        .getDeclaredConstructor()
                        .newInstance();

        Method method =
                controllerClass.getDeclaredMethod(
                        methodName
                );

        return new HandlerMethod(
                controller,
                method
        );
    }

    static class NormalController {

        public void normal() {
        }
    }

    @Compress(minResponseSize = 4096)
    static class ClassAnnotatedController {

        public void compressed() {
        }
    }

    static class CompressedController {

        @Compress(minResponseSize = 2048)
        public void compressed() {
        }
    }

    @Compress(minResponseSize = 4096)
    static class BothAnnotatedController {

        @Compress(minResponseSize = 2048)
        public void compressed() {
        }
    }
}
