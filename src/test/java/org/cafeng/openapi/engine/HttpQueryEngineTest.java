package org.cafeng.openapi.engine;

import org.cafeng.openapi.definition.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TDD tests for HttpQueryEngine.
 */
@ExtendWith(MockitoExtension.class)
class HttpQueryEngineTest {

    private HttpQueryEngine engine;

    @BeforeEach
    void setUp() {
        engine = new HttpQueryEngine();
    }

    @SuppressWarnings("unchecked")
    private String invokeBuildUrlWithParams(String baseUrl, Map<String, Object> params) throws Exception {
        Method method = HttpQueryEngine.class.getDeclaredMethod("buildUrlWithParams", String.class, Map.class);
        method.setAccessible(true);
        return (String) method.invoke(engine, baseUrl, params);
    }

    @Test
    void getType_shouldReturnHttp() {
        assertEquals("http", engine.getType());
    }

    @Test
    void buildUrlWithParams_noParams_returnsBaseUrl() throws Exception {
        assertEquals("https://api.example.com/users",
                invokeBuildUrlWithParams("https://api.example.com/users", null));
        assertEquals("https://api.example.com/users",
                invokeBuildUrlWithParams("https://api.example.com/users", Map.of()));
    }

    @Test
    void buildUrlWithParams_singleParam_appendsCorrectly() throws Exception {
        String result = invokeBuildUrlWithParams("https://api.example.com/users",
                Map.of("status", "ACTIVE"));
        assertEquals("https://api.example.com/users?status=ACTIVE", result);
    }

    @Test
    void buildUrlWithParams_multipleParams_appendsWithAmpersands() throws Exception {
        // LinkedHashMap to preserve insertion order
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("status", "ACTIVE");
        params.put("page", "2");
        String result = invokeBuildUrlWithParams("https://api.example.com/users", params);
        assertEquals("https://api.example.com/users?status=ACTIVE&page=2", result);
    }

    @Test
    void buildUrlWithParams_baseUrlHasExistingQuery_noDoubleAmpersand() throws Exception {
        String baseUrl = "https://api.example.com/users?existing=1";
        Map<String, Object> params = Map.of("status", "ACTIVE");
        String result = invokeBuildUrlWithParams(baseUrl, params);
        // Must be ?existing=1&status=ACTIVE, NOT ?existing=1&&status=ACTIVE
        assertEquals("https://api.example.com/users?existing=1&status=ACTIVE", result);
        assertFalse(result.contains("&&"), "URL must not contain double ampersand");
    }

    @Test
    void buildUrlWithParams_baseUrlHasExistingQuery_multipleParams() throws Exception {
        String baseUrl = "https://api.example.com/users?existing=1";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("status", "ACTIVE");
        params.put("page", "3");
        String result = invokeBuildUrlWithParams(baseUrl, params);
        assertEquals("https://api.example.com/users?existing=1&status=ACTIVE&page=3", result);
    }

    @Test
    void buildUrlWithParams_specialCharsInValue_urlEncoded() throws Exception {
        Map<String, Object> params = Map.of("q", "hello world");
        String result = invokeBuildUrlWithParams("https://api.example.com/search", params);
        assertTrue(result.contains("q=hello+world") || result.contains("q=hello%20world"),
                "Space in value must be URL-encoded. Got: " + result);
    }

    @Test
    void buildUrlWithParams_ampersandInValue_urlEncoded() throws Exception {
        Map<String, Object> params = Map.of("q", "a&b");
        String result = invokeBuildUrlWithParams("https://api.example.com/search", params);
        // The raw '&' must be encoded as %26, not appear literally
        assertTrue(result.contains("q=a%26b"),
                "'&' in value must be URL-encoded. Got: " + result);
    }

    @Test
    void buildUrlWithParams_equalsInValue_urlEncoded() throws Exception {
        Map<String, Object> params = Map.of("expr", "x=y");
        String result = invokeBuildUrlWithParams("https://api.example.com/search", params);
        assertTrue(result.contains("expr=x%3Dy"),
                "'=' in value must be URL-encoded. Got: " + result);
    }

    @Test
    void buildUrlWithParams_integerValue_convertedToString() throws Exception {
        Map<String, Object> params = Map.of("page", 42);
        String result = invokeBuildUrlWithParams("https://api.example.com/users", params);
        assertEquals("https://api.example.com/users?page=42", result);
    }
}
