package cn.slainc.web.doc;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.customizers.OpenApiCustomizer;

// 向 components 注册共享的 ResultAny 模型，供 ResultOperationCustomizer 为缺响应结构的端点补引用
public class ResultSchemaCustomizer implements OpenApiCustomizer {

    public static final String SCHEMA_NAME = "ResultAny";

    public static final String SCHEMA_REF = "#/components/schemas/" + SCHEMA_NAME;

    @Override
    public void customise(OpenAPI openApi) {
        Components components = openApi.getComponents();
        if (components == null) {
            components = new Components();
            openApi.setComponents(components);
        }
        if (components.getSchemas() != null && components.getSchemas().containsKey(SCHEMA_NAME)) {
            return;
        }
        components.addSchemas(SCHEMA_NAME, new ObjectSchema()
                .title(SCHEMA_NAME)
                .description("统一响应包装，data 结构随接口而定")
                .addProperty("code", new IntegerSchema().format("int32").description("业务码，成功为 200"))
                .addProperty("message", new StringSchema().description("提示信息"))
                .addProperty("data", new Schema<>().description("业务数据，结构随接口而定"))
                .addProperty("timestamp", new IntegerSchema().format("int64").description("毫秒级 Unix 时间戳")));
    }
}
