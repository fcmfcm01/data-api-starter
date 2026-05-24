package com.example.restclientspike;

import org.junit.jupiter.api.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Standalone spike verifying RestClient HTTP forwarding via MockRestServiceServer.
 */
class RestclientSpikeTest {

    private RestClient.Builder builder;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        builder = RestClient.builder().requestFactory(requestFactory);
        mockServer = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void restClientBuilderCanBeCreated() {
        assertNotNull(RestClient.builder());
    }

    @Test
    void restClientBuilderWithRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(3));
        assertNotNull(RestClient.builder().requestFactory(factory).build());
    }

    @Test
    void restClientCanMakeMockedGetRequest() {
        mockServer.expect(requestTo("https://example.com/api/data"))
                .andRespond(withSuccess("{\"items\":[{\"id\":1,\"name\":\"test\"}]}",
                        MediaType.APPLICATION_JSON));

        RestClient client = builder.build();
        String response = client.get()
                .uri("https://example.com/api/data")
                .retrieve()
                .body(String.class);

        assertNotNull(response);
        assertTrue(response.contains("items"));
        assertTrue(response.contains("test"));
        mockServer.verify();
    }

    @Test
    void restClientHandlesJsonResponseAsMap() {
        mockServer.expect(requestTo("https://api.example.com/users/1"))
                .andRespond(withSuccess("{\"data\":{\"id\":1,\"name\":\"John\"}}",
                        MediaType.APPLICATION_JSON));

        @SuppressWarnings("unchecked")
        Map<String, Object> response = builder.build().get()
                .uri("https://api.example.com/users/1")
                .retrieve()
                .body(Map.class);

        assertNotNull(response);
        assertTrue(response.containsKey("data"));
        mockServer.verify();
    }

    @Test
    void restClientUnwrapsItemsKey() {
        mockServer.expect(requestTo("https://api.example.com/products"))
                .andRespond(withSuccess("{\"items\":[{\"id\":10},{\"id\":20}]}",
                        MediaType.APPLICATION_JSON));

        @SuppressWarnings("unchecked")
        Map<String, Object> response = builder.build().get()
                .uri("https://api.example.com/products")
                .retrieve()
                .body(Map.class);

        assertTrue(response.containsKey("items"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
        assertEquals(2, items.size());
        assertEquals(10, items.get(0).get("id"));
        mockServer.verify();
    }

    @Test
    void restClientUnwrapsResultsKey() {
        mockServer.expect(requestTo("https://api.example.com/search"))
                .andRespond(withSuccess("{\"results\":[{\"x\":42}]}",
                        MediaType.APPLICATION_JSON));

        @SuppressWarnings("unchecked")
        Map<String, Object> response = builder.build().get()
                .uri("https://api.example.com/search")
                .retrieve()
                .body(Map.class);

        assertTrue(response.containsKey("results"));
        mockServer.verify();
    }

    @Test
    void restClientUnwrapsDataKey() {
        mockServer.expect(requestTo("https://api.example.com/r1"))
                .andRespond(withSuccess("{\"data\":[{\"id\":1},{\"id\":2}]}",
                        MediaType.APPLICATION_JSON));

        @SuppressWarnings("unchecked")
        Map<String, Object> response = builder.build().get()
                .uri("https://api.example.com/r1")
                .retrieve()
                .body(Map.class);

        assertTrue(response.containsKey("data"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
        assertEquals(2, data.size());
        mockServer.verify();
    }

    @Test
    void restClientHandlesFlatObjectAsSingleResult() {
        mockServer.expect(requestTo("https://api.example.com/me"))
                .andRespond(withSuccess("{\"id\":1,\"name\":\"Alice\",\"role\":\"admin\"}",
                        MediaType.APPLICATION_JSON));

        @SuppressWarnings("unchecked")
        Map<String, Object> response = builder.build().get()
                .uri("https://api.example.com/me")
                .retrieve()
                .body(Map.class);

        assertEquals(1, response.get("id"));
        assertEquals("admin", response.get("role"));
        mockServer.verify();
    }

    @Test
    void restClientExtractsTotalFromBody() {
        mockServer.expect(requestTo("https://api.example.com/paged"))
                .andRespond(withSuccess("{\"data\":[{\"id\":1}],\"total\":42}",
                        MediaType.APPLICATION_JSON));

        @SuppressWarnings("unchecked")
        Map<String, Object> response = builder.build().get()
                .uri("https://api.example.com/paged")
                .retrieve()
                .body(Map.class);

        assertEquals(42, response.get("total"));
        mockServer.verify();
    }

    @Test
    void restClientCanMakeMockedPostRequest() {
        mockServer.expect(requestTo("https://api.example.com/users"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"data\":[{\"id\":2,\"name\":\"Bob\"}]}",
                        MediaType.APPLICATION_JSON));

        @SuppressWarnings("unchecked")
        Map<String, Object> response = builder.build().post()
                .uri("https://api.example.com/users")
                .body(Map.of("name", "Bob", "email", "bob@test.com"))
                .retrieve()
                .body(Map.class);

        assertTrue(response.containsKey("data"));
        mockServer.verify();
    }

    @Test
    void restClientHandlesServerError() {
        mockServer.expect(requestTo("https://api.example.com/fail"))
                .andRespond(withServerError());

        assertThrows(Exception.class, () ->
                builder.build().get()
                        .uri("https://api.example.com/fail")
                        .retrieve()
                        .body(String.class));
    }

    @Test
    void restClientBuilderSupportsReadTimeout() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofMillis(2500));
        RestClient client = RestClient.builder().requestFactory(factory).build();
        assertNotNull(client);
    }

    @Test
    void mockRestServiceServerVerifiesAllExpectations() {
        mockServer.expect(requestTo("https://api.example.com/a"))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo("https://api.example.com/b"))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        RestClient client = builder.build();
        client.get().uri("https://api.example.com/a").retrieve().body(String.class);
        client.get().uri("https://api.example.com/b").retrieve().body(String.class);

        mockServer.verify();
    }
}
