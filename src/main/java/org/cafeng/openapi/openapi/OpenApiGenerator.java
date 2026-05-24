package org.cafeng.openapi.openapi;

import org.cafeng.openapi.definition.ApiDefinition;
import org.cafeng.openapi.definition.ResponseField;
import org.cafeng.openapi.registry.ApiDefinitionRegistry;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Generates an OpenAPI 3.0 specification from registered API definitions.
 *
 * <p>Produces paths, operations, parameters, and response schemas that feed
 * into Swagger UI. Field descriptions include scope level and PII markers.</p>
 */
@Component
public class OpenApiGenerator {

    private final ApiDefinitionRegistry apiRegistry;

    public OpenApiGenerator(ApiDefinitionRegistry apiRegistry) {
        this.apiRegistry = apiRegistry;
    }

    /**
     * Backward-compatible constructor for tests that don't use the shared registry.
     */
    public OpenApiGenerator() {
        this.apiRegistry = new ApiDefinitionRegistry();
    }

    public void registerApi(ApiDefinition apiDefinition) {
        apiRegistry.register(apiDefinition);
    }

    public OpenAPI generate() {
        OpenAPI openAPI = new OpenAPI();
        openAPI.setInfo(new Info()
                .title("Data API")
                .version("1.0.0")
                .description("Auto-generated API documentation"));

        io.swagger.v3.oas.models.Paths paths = new io.swagger.v3.oas.models.Paths();
        
        for (ApiDefinition api : apiRegistry.getAll()) {
            io.swagger.v3.oas.models.PathItem pathItem = new io.swagger.v3.oas.models.PathItem();
            
            io.swagger.v3.oas.models.Operation operation = new io.swagger.v3.oas.models.Operation();
            operation.setSummary(api.name());
            operation.setDescription(api.id());

            List<Parameter> parameters = new ArrayList<>();
            if (api.parameters() != null) {
                for (var param : api.parameters()) {
                    Parameter parameter = new Parameter();
                    parameter.setName(param.name());
                    parameter.setIn(param.in());
                    parameter.setRequired(param.required());
                    parameter.setDescription(param.description());
                    
                    Schema<?> schema = new Schema<>();
                    schema.setType(param.type());
                    parameter.setSchema(schema);
                    
                    parameters.add(parameter);
                }
            }
            operation.setParameters(parameters);

            ApiResponses responses = new ApiResponses();
            io.swagger.v3.oas.models.responses.ApiResponse response = new io.swagger.v3.oas.models.responses.ApiResponse();
            response.setDescription(api.response() != null ? api.response().type() : "Success");
            
            Content content = new Content();
            MediaType mediaType = new MediaType();
            Schema<?> responseSchema = new Schema<>();
            responseSchema.setType("object");

            if (api.response() != null && api.response().fields() != null) {
                Map<String, Schema> properties = new LinkedHashMap<>();
                for (var field : api.response().fields()) {
                    Schema fieldSchema = new Schema<>();
                    fieldSchema.setType("string");
                    fieldSchema.setDescription(buildFieldDescription(field));
                    properties.put(field.name(), fieldSchema);
                }
                responseSchema.setProperties(properties);
            }

            mediaType.setSchema(responseSchema);
            content.addMediaType("application/json", mediaType);
            response.setContent(content);
            responses.addApiResponse("200", response);
            operation.setResponses(responses);

            switch (api.method().toUpperCase()) {
                case "GET" -> pathItem.setGet(operation);
                case "POST" -> pathItem.setPost(operation);
                case "PUT" -> pathItem.setPut(operation);
                case "DELETE" -> pathItem.setDelete(operation);
            }

            paths.addPathItem(api.path(), pathItem);
        }

        openAPI.setPaths(paths);
        return openAPI;
    }

    public Collection<ApiDefinition> getRegisteredApis() {
        return apiRegistry.getAll();
    }

    private String buildFieldDescription(ResponseField field) {
        String base = field.description() != null ? field.description() : "";
        String scope = field.scope() != null ? field.scope() : "basic";

        StringBuilder sb = new StringBuilder(base);
        if (!base.isEmpty()) sb.append(" ");
        sb.append("[scope: ").append(scope);
        if (field.pii()) {
            sb.append(", pii");
        }
        sb.append("]");

        return sb.toString();
    }
}
