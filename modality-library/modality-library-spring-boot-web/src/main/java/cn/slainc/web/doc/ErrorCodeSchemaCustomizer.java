package cn.slainc.web.doc;

import cn.slainc.core.result.ResultCode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.List;

public class ErrorCodeSchemaCustomizer implements OpenApiCustomizer {

    private static final String TITLE = "统一错误码";

    private final List<ResultCode> resultCodes;

    public ErrorCodeSchemaCustomizer() {
        this(List.of(ResultCode.values()));
    }

    public ErrorCodeSchemaCustomizer(List<ResultCode> resultCodes) {
        this.resultCodes = resultCodes;
    }

    @Override
    public void customise(OpenAPI openApi) {
        Info info = openApi.getInfo();
        if (info == null) {
            return;
        }
        String description = info.getDescription() == null ? "" : info.getDescription();
        if (description.contains(TITLE)) {
            return;
        }
        String table = buildTable();
        info.setDescription(description.isBlank() ? table : description + "\n\n" + table);
    }

    private String buildTable() {
        StringBuilder builder = new StringBuilder("#### ").append(TITLE).append("\n\n");
        builder.append("| 错误码 | 说明 |\n| --- | --- |\n");
        for (ResultCode resultCode : resultCodes) {
            builder.append("| ").append(resultCode.code()).append(" | ").append(resultCode.message()).append(" |\n");
        }
        return builder.toString();
    }
}
