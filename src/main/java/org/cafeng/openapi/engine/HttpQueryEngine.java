package org.cafeng.openapi.engine;

import org.cafeng.openapi.definition.ApiDefinition;
import org.cafeng.openapi.definition.ApiSource;
import org.cafeng.openapi.error.DataApiException;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP-backed query engine that forwards requests to an upstream service.
 *
 * <p>Supports GET (parameters appended as query string) and POST/PUT
 * (parameters sent as JSON body). Response unwrapping looks for common
 * wrapper keys ({@code data}, {@code items}, {@code results}, {@code records},
 * {@code list}) and falls back to treating the body as a single result.</p>
 */
public class HttpQueryEngine implements QueryEngine {

    private final RestTemplate overrideRestTemplate;
    private final ConcurrentHashMap<Integer, RestTemplate> restTemplateCache = new ConcurrentHashMap<>();

    public HttpQueryEngine() {
        this.overrideRestTemplate = null;
    }

    public HttpQueryEngine(RestTemplate restTemplate) {
        this.overrideRestTemplate = restTemplate;
    }

    private RestTemplate buildRestTemplate(int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(5000));
        factory.setReadTimeout(Duration.ofMillis(30000));
        return new RestTemplate(factory);
    }

    @Override
    public String getType() {
        return "http";
    }

    @Override
    @SuppressWarnings("unchecked")
    public QueryResult execute(ApiDefinition apiDefinition, String ignoredSql, Map<String, Object> params) {
        ApiSource source = apiDefinition.source();
        String baseUrl = source.url();
        String method = source.method() != null ? source.method().toUpperCase() : "GET";
        int timeout = source.timeout() > 0 ? source.timeout() : 5000;
        RestTemplate restTemplate = overrideRestTemplate != null
                ? overrideRestTemplate
                : restTemplateCache.computeIfAbsent(timeout, this::buildRestTemplate);

        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new DataApiException("HTTP source requires 'url' to be configured");
        }

        try {
            // Build URL with query params for GET, or body for POST/PUT
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Add configured static headers
            if (source.headers() != null) {
                source.headers().forEach(headers::set);
            }

            HttpEntity<?> entity;
            ResponseEntity<Map> response;

            if ("GET".equals(method)) {
                // Append params as query string
                String url = buildUrlWithParams(baseUrl, params);
                entity = new HttpEntity<>(headers);
                response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            } else {
                entity = new HttpEntity<>(params, headers);
                response = restTemplate.exchange(baseUrl, HttpMethod.valueOf(method), entity, Map.class);
            }

            Map<String, Object> body = response.getBody();
            if (body == null) {
                return new QueryResult(List.of(), 0, 0);
            }

            // Unwrap: if body contains a "data" or "items" array, use that
            List<Map<String, Object>> data = extractData(body);
            long total = extractTotal(body, data.size());

            return new QueryResult(data, total, 0);

        } catch (RestClientException e) {
            throw new DataApiException("HTTP forward failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractData(Map<String, Object> body) {
        // Try common wrapper keys
        for (String key : List.of("data", "items", "results", "records", "list")) {
            Object val = body.get(key);
            if (val instanceof List<?> list) {
                List<Map<String, Object>> result = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        map.forEach((k, v) -> row.put(String.valueOf(k), v));
                        result.add(row);
                    }
                }
                return result;
            }
        }
        // If body itself is a flat object (single result), wrap it
        return List.of(body);
    }

    private long extractTotal(Map<String, Object> body, int dataSize) {
        for (String key : List.of("total", "total_count", "totalCount", "count")) {
            Object val = body.get(key);
            if (val instanceof Number n) {
                return n.longValue();
            }
        }
        return dataSize;
    }

    private String buildUrlWithParams(String baseUrl, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return baseUrl;
        }
        StringBuilder sb = new StringBuilder(baseUrl);
        boolean hasQuery = baseUrl.contains("?");
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (hasQuery) {
                sb.append("&");
            } else {
                sb.append("?");
                hasQuery = true;
            }
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
              .append("=")
              .append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
