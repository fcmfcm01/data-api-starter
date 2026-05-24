package org.cafeng.openapi.openapi;

import org.cafeng.openapi.definition.*;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OpenApiGeneratorTest {

    private final OpenApiGenerator generator = new OpenApiGenerator();

    private ApiDefinition createTestApi(String id, String path, String method) {
        return new ApiDefinition(
                id, "Test " + id, path, method,
                List.of(new ApiParameter("name", "query", "string", false, "test param", null, null, null, null, null)),
                new ApiSource("jdbc", "ds", "SELECT 1"),
                new ApiResponse("list", List.of(new ResponseField("id", "basic", false, null))),
                Map.of("read", "basic"), new ApiSla(3000, 100)
        );
    }

    @Test
    void shouldRegisterApi() {
        var api = createTestApi("test-1", "/api/test", "GET");
        generator.registerApi(api);

        Collection<ApiDefinition> apis = generator.getRegisteredApis();
        assertEquals(1, apis.size());
        assertEquals("test-1", apis.iterator().next().id());
    }

    @Test
    void shouldRegisterMultipleApis() {
        generator.registerApi(createTestApi("api-1", "/api/1", "GET"));
        generator.registerApi(createTestApi("api-2", "/api/2", "POST"));

        assertEquals(2, generator.getRegisteredApis().size());
    }

    @Test
    void shouldOverwriteApiWithSameId() {
        generator.registerApi(createTestApi("api-1", "/api/old", "GET"));
        generator.registerApi(createTestApi("api-1", "/api/new", "GET"));

        assertEquals(1, generator.getRegisteredApis().size());
        assertEquals("/api/new", generator.getRegisteredApis().iterator().next().path());
    }

    @Test
    void shouldGenerateOpenApiDoc() {
        generator.registerApi(createTestApi("list-users", "/v1/users", "GET"));

        var openApi = generator.generate();

        assertNotNull(openApi);
        assertNotNull(openApi.getInfo());
        assertEquals("Data API", openApi.getInfo().getTitle());
        assertEquals("1.0.0", openApi.getInfo().getVersion());
        assertNotNull(openApi.getPaths());
        assertTrue(openApi.getPaths().containsKey("/v1/users"));
    }

    @Test
    void shouldGenerateOperationWithCorrectMethod() {
        generator.registerApi(createTestApi("create-user", "/v1/users", "POST"));

        var openApi = generator.generate();
        var pathItem = openApi.getPaths().get("/v1/users");

        assertNotNull(pathItem.getPost());
        assertNull(pathItem.getGet());
        assertEquals("Test create-user", pathItem.getPost().getSummary());
    }

    @Test
    void shouldIncludeParametersInOperation() {
        generator.registerApi(createTestApi("test-api", "/api/test", "GET"));

        var openApi = generator.generate();
        var operation = openApi.getPaths().get("/api/test").getGet();

        assertNotNull(operation.getParameters());
        assertEquals(1, operation.getParameters().size());
        assertEquals("name", operation.getParameters().get(0).getName());
        assertEquals("query", operation.getParameters().get(0).getIn());
        assertFalse(operation.getParameters().get(0).getRequired());
    }

    @Test
    void shouldInclude200Response() {
        generator.registerApi(createTestApi("test-api", "/api/test", "GET"));

        var openApi = generator.generate();
        var responses = openApi.getPaths().get("/api/test").getGet().getResponses();

        assertNotNull(responses);
        assertNotNull(responses.get("200"));
    }

    @Test
    void shouldHandleEmptyRegistry() {
        var openApi = generator.generate();

        assertNotNull(openApi);
        assertNotNull(openApi.getInfo());
        assertTrue(openApi.getPaths().isEmpty());
    }

    @Test
    void shouldPreserveRegistrationOrder() {
        generator.registerApi(createTestApi("api-c", "/c", "GET"));
        generator.registerApi(createTestApi("api-a", "/a", "GET"));
        generator.registerApi(createTestApi("api-b", "/b", "GET"));

        var ids = generator.getRegisteredApis().stream()
                .map(ApiDefinition::id)
                .toList();
        assertEquals(List.of("api-c", "api-a", "api-b"), ids);
    }

    @Test
    void shouldIncludeScopeInfoInFieldDescription() {
        ApiDefinition api = new ApiDefinition(
                "scope-test", "Scope Test", "/api/scope-test", "GET",
                List.of(),
                new ApiSource("jdbc", "ds", "SELECT * FROM t"),
                new ApiResponse("single", List.of(
                        new ResponseField("name", "basic", false, "Name"),
                        new ResponseField("salary", "sensitive", true, "Salary"),
                        new ResponseField("dept", null, false, null)
                )),
                Map.of(), new ApiSla(5000, 100)
        );
        generator.registerApi(api);

        var openApi = generator.generate();
        var response = openApi.getPaths().get("/api/scope-test").getGet().getResponses().get("200");
        var mediaType = response.getContent().get("application/json");
        Schema<?> schema = mediaType.getSchema();

        assertNotNull(schema.getProperties());
        assertNotNull(schema.getProperties().get("name"));
        assertNotNull(schema.getProperties().get("salary"));
        assertNotNull(schema.getProperties().get("dept"));

        // basic scope, no pii
        Schema<?> nameField = (Schema<?>) schema.getProperties().get("name");
        assertEquals("Name [scope: basic]", nameField.getDescription());

        // sensitive scope with pii
        Schema<?> salaryField = (Schema<?>) schema.getProperties().get("salary");
        assertEquals("Salary [scope: sensitive, pii]", salaryField.getDescription());

        // null scope defaults to "basic", null description
        Schema<?> deptField = (Schema<?>) schema.getProperties().get("dept");
        assertEquals("[scope: basic]", deptField.getDescription());
    }
}
