package com.compression.compression_library.processor;

import com.compression.compression_library.annotation.Compress;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Set;

@SupportedAnnotationTypes(
        "com.compression.compression_library.annotation.Compress"
)
@SupportedSourceVersion(
        SourceVersion.RELEASE_21
)
public class CompressAnnotationProcessor
        extends AbstractProcessor {

    @Override
    public boolean process(
            Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv
    ) {

        for (Element element :
                roundEnv.getElementsAnnotatedWith(
                        Compress.class
                )) {

            /*
             * We only need to validate method-level
             * annotations here.
             */
            if (element.getKind()
                    != ElementKind.METHOD) {

                continue;
            }

            ExecutableElement method =
                    (ExecutableElement) element;

            Element enclosing =
                    method.getEnclosingElement();

            if (!(enclosing instanceof TypeElement type)) {
                continue;
            }

            if (hasCompress(type)) {

                processingEnv
                        .getMessager()
                        .printMessage(
                                Diagnostic.Kind.ERROR,

                                "@Compress cannot be declared on "
                                        + "endpoint method '"
                                        + method.getSimpleName()
                                        + "' because controller class '"
                                        + type.getQualifiedName()
                                        + "' already has @Compress. "
                                        + "Remove @Compress from either "
                                        + "the class or the method.",

                                method
                        );
            }
        }

        return true;
    }

    private boolean hasCompress(
            TypeElement type
    ) {

        for (AnnotationMirror annotation :
                type.getAnnotationMirrors()) {

            String annotationName =
                    annotation
                            .getAnnotationType()
                            .asElement()
                            .toString();

            if (Compress.class
                    .getCanonicalName()
                    .equals(annotationName)) {

                return true;
            }
        }

        return false;
    }
}
