package org.cafeng.openapi.engine;

import org.cafeng.openapi.definition.*;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * TDD tests for HttpQueryEngine using MockRestServiceServer.
 */
class HttpQueryEngineIntegrationTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private HttpQueryEngine engine;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        engine = new HttpQueryEngine(restTemplate);
    }

    private ApiDefinition httpApi(String url, String method) {
        return new ApiDefinition(
            "test-http", "Test HTTP", "/api/test", "GET",
            List.of(),
            new ApiSource("http", null, null, url, method, null, 0),
            new ApiResponse("list", List.of()),
            Map.of(), null
        );
    }

    @Test
    void execute_getWithParams_shouldAppendQueryString() throws Exception {
        // Use LinkedHashMap for predictable order
        mockServer.expect(requestTo("https://api.example.com/users?name=Alice&age=30"))
                .andRespond(withSuccess("{\"data\":[{\"id\":1,\"name\":\"Alice\"}],\"total\":1}", MediaType.APPLICATION_JSON));

        ApiDefinition api = httpApi("https://api.example.com/users", "GET");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "Alice");
        params.put("age", 30);

        QueryResult result = engine.execute(api, null, params);

        assertEquals(1, result.data().size());
        assertEquals("Alice", result.data().get(0).get("name"));
        assertEquals(1, result.totalCount());
        mockServer.verify();
    }

    @Test
    void execute_postWithBody_shouldSendJson() throws Exception {
        mockServer.expect(requestTo("https://api.example.com/users"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"data\":[{\"id\":2,\"name\":\"Bob\"}]}", MediaType.APPLICATION_JSON));

        ApiDefinition api = httpApi("https://api.example.com/users", "POST");
        Map<String, Object> params = Map.of("name", "Bob", "email", "bob@test.com");

        QueryResult result = engine.execute(api, null, params);

        assertEquals(1, result.data().size());
        assertEquals("Bob", result.data().get(0).get("name"));
        mockServer.verify();
    }

    @Test
    void execute_unwrapItemsKey() throws Exception {
        mockServer.expect(requestTo("https://api.example.com/products"))
                .andRespond(withSuccess("{\"items\":[{\"id\":1},{\"id\":2}],\"total\":2}", MediaType.APPLICATION_JSON));

        ApiDefinition api = httpApi("https://api.example.com/products", "GET");
        QueryResult result = engine.execute(api, null, Map.of());

        assertEquals(2, result.data().size());
        assertEquals(2, result.totalCount());
    }

    @Test
    void execute_flatObject_singleResult() throws Exception {
        mockServer.expect(requestTo("https://api.example.com/me"))
                .andRespond(withSuccess("{\"id\":1,\"name\":\"Alice\",\"role\":\"admin\"}", MediaType.APPLICATION_JSON));

        ApiDefinition api = httpApi("https://api.example.com/me", "GET");
        QueryResult result = engine.execute(api, null, Map.of());

        assertEquals(1, result.data().size());
        assertEquals("admin", result.data().get(0).get("role"));
    }

    @Test
    void execute_nullBody_returnsEmpty() throws Exception {
        mockServer.expect(requestTo("https://api.example.com/empty"))
                .andRespond(withSuccess());

        ApiDefinition api = httpApi("https://api.example.com/empty", "GET");
        QueryResult result = engine.execute(api, null, Map.of());

        assertTrue(result.data().isEmpty());
        assertEquals(0, result.totalCount());
    }

    @Test
    void execute_upstreamError_throwsRuntimeException() throws Exception {
        mockServer.expect(requestTo("https://api.example.com/fail"))
                .andRespond(withServerError());

        ApiDefinition api = httpApi("https://api.example.com/fail", "GET");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> engine.execute(api, null, Map.of()));
        assertTrue(ex.getMessage().contains("HTTP forward failed"));
    }

    @Test
    void execute_missingUrl_throwsAtConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> new ApiSource("http", null, null, null, null, null, 0));
    }
}
