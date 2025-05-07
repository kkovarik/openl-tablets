package org.openl.rules.spring.openapi.converter;

import java.util.Arrays;
import java.util.Iterator;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import org.openl.rules.spring.openapi.service.OpenApiPropertyResolver;
import org.openl.util.StringUtils;

/**
 * Schema customizer. The purpose of this class is to support {@link Deprecated}, {@link Parameter} annotations when
 * they are defined on class properties. Original v3 implementation doesn't support this case. Also, it's used for
 * schema description localization.
 *
 * @author Vladyslav Pikus
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PropertySchemaCustomizingConverter implements ModelConverter {

    private final OpenApiPropertyResolver apiPropertyResolver;

    public PropertySchemaCustomizingConverter(OpenApiPropertyResolver apiPropertyResolver) {
        this.apiPropertyResolver = apiPropertyResolver;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        if (chain.hasNext()) {
            var resolvedSchema = chain.next().resolve(type, context, chain);
            if (resolvedSchema == null) {
                return null;
            }
            if (type.isSchemaProperty()) {
                if (type.getCtxAnnotations() != null) {
                    for (var anno : type.getCtxAnnotations()) {
                        if (anno.annotationType() == Deprecated.class) {
                            resolvedSchema.setDeprecated(Boolean.TRUE);
                        } else if (anno instanceof Parameter) {
                            // Support Parameter when it's defined on class field. Swagger doesn't support this case
                            var paramApi = (Parameter) anno;
                            if (StringUtils.isNotBlank(paramApi.description())) {
                                resolvedSchema.setDescription(paramApi.description());
                            }
                            if (StringUtils.isNotBlank(paramApi.example())) {
                                resolvedSchema.setExample(paramApi.example());
                            }
                            var schemaApi = paramApi.schema();
                            if (schemaApi != null && schemaApi.allowableValues().length > 0) {
                                resolvedSchema.setEnum(Arrays.asList(schemaApi.allowableValues()));
                            }
                            if (paramApi.required()) {
                                var parentSchema = type.getParent();
                                if (!CollectionUtils.containsInstance(parentSchema.getRequired(), type.getPropertyName())) {
                                    parentSchema.addRequiredItem(type.getPropertyName());
                                }
                            }
                        }
                    }
                }
            }
            if (StringUtils.isNotBlank(resolvedSchema.getDescription())) {
                resolvedSchema.setDescription(apiPropertyResolver.resolve(resolvedSchema.getDescription()));
            }
            return resolvedSchema;
        }
        return null;
    }
}
