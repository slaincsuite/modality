package cn.slainc.web.doc;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = {"io.swagger.v3.oas.models.OpenAPI", "org.springdoc.core.models.GroupedOpenApi",
        "org.springdoc.core.customizers.OpenApiCustomizer"})
@ConditionalOnProperty(prefix = "modality.web.open-api", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(OpenApiProperties.class)
public class OpenApiConfig {

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI openApi(OpenApiProperties properties) {
        Components components = new Components().addSecuritySchemes(properties.getBearerSchemeName(),
                new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .in(SecurityScheme.In.HEADER)
                        .name("Authorization"));
        OpenAPI openApi = new OpenAPI()
                .info(new Info()
                        .title(properties.getTitle())
                        .description(properties.getDescription())
                        .version(properties.getVersion()))
                .components(components);
        if (properties.isSecurityRequired()) {
            openApi.addSecurityItem(new SecurityRequirement().addList(properties.getBearerSchemeName()));
        }
        properties.getServers().forEach((url, description) ->
                openApi.addServersItem(new Server().url(url).description(description)));
        return openApi;
    }

    @Bean
    @ConditionalOnMissingBean(EnumSchemaCustomizer.class)
    @ConditionalOnProperty(prefix = "modality.web.open-api", name = "enum-display", havingValue = "true",
            matchIfMissing = true)
    public EnumSchemaCustomizer enumSchemaCustomizer() {
        return new EnumSchemaCustomizer();
    }

    @Bean
    @ConditionalOnMissingBean(ErrorCodeSchemaCustomizer.class)
    @ConditionalOnProperty(prefix = "modality.web.open-api", name = "error-codes", havingValue = "true",
            matchIfMissing = true)
    public ErrorCodeSchemaCustomizer errorCodeSchemaCustomizer() {
        return new ErrorCodeSchemaCustomizer();
    }

    @Bean
    @ConditionalOnMissingBean(GroupedOpenApi.class)
    public GroupedOpenApi groupedOpenApi(OpenApiProperties properties,
                                                 ObjectProvider<EnumSchemaCustomizer> enumSchemaCustomizer,
                                                 ObjectProvider<ErrorCodeSchemaCustomizer> errorCodeSchemaCustomizer) {
        GroupedOpenApi.Builder builder = GroupedOpenApi.builder().group(properties.getGroup());
        if (!properties.getPaths().isEmpty()) {
            builder.pathsToMatch(properties.getPaths().toArray(new String[0]));
        }
        if (!properties.getPackages().isEmpty()) {
            builder.packagesToScan(properties.getPackages().toArray(new String[0]));
        }
        enumSchemaCustomizer.ifAvailable(builder::addOpenApiCustomizer);
        errorCodeSchemaCustomizer.ifAvailable(builder::addOpenApiCustomizer);
        return builder.build();
    }
}
