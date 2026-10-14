package cn.slainc.web.doc;

import cn.slainc.web.result.NoWrap;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import jakarta.servlet.http.HttpServletResponse;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.web.method.HandlerMethod;

import java.util.Map;

// 补全 200 响应结构：流式导出标 binary，缺响应结构的端点引用 ResultAny；@NoWrap 端点原样返回
public class ResultOperationCustomizer implements GlobalOperationCustomizer {

    private static final String OK = "200";

    private static final String BINARY_MEDIA_TYPE = "application/octet-stream";

    private static final String JSON_MEDIA_TYPE = "application/json";

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        if (handlerMethod.hasMethodAnnotation(NoWrap.class) || handlerMethod.getBeanType().isAnnotationPresent(NoWrap.class)) {
            return operation;
        }
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            return operation;
        }
        boolean streaming = writesToResponse(handlerMethod);
        ApiResponse ok = responses.get(OK);
        if (ok == null) {
            if (!streaming) {
                return operation;
            }
            ok = new ApiResponse().description("OK");
            responses.put(OK, ok);
        }
        if (streaming) {
            ok.setContent(new Content().addMediaType(BINARY_MEDIA_TYPE,
                    new MediaType().schema(new Schema<String>().type("string").format("binary"))));
            return operation;
        }
        if (lacksSchema(ok.getContent())) {
            ok.setContent(new Content().addMediaType(JSON_MEDIA_TYPE,
                    new MediaType().schema(new Schema<>().$ref(ResultSchemaCustomizer.SCHEMA_REF))));
        }
        return operation;
    }

    private boolean writesToResponse(HandlerMethod handlerMethod) {
        for (var parameter : handlerMethod.getMethodParameters()) {
            if (HttpServletResponse.class.equals(parameter.getParameterType())) {
                return true;
            }
        }
        return false;
    }

    // 无任何 mediaType 携带实质结构（引用、属性或 additionalProperties）时视为缺响应结构
    private boolean lacksSchema(Content content) {
        if (content == null || content.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, MediaType> entry : content.entrySet()) {
            Schema<?> schema = entry.getValue().getSchema();
            if (schema == null) {
                continue;
            }
            if (schema.get$ref() != null
                    || (schema.getProperties() != null && !schema.getProperties().isEmpty())
                    || schema.getAdditionalProperties() != null) {
                return false;
            }
        }
        return true;
    }
}
