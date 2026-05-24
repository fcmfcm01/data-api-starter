package org.cafeng.openapi.engine;

import org.cafeng.openapi.definition.ApiDefinition;
import org.cafeng.openapi.definition.ApiSource;
import org.cafeng.openapi.error.DataApiException;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.time.Duration;
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

    private static final HttpClient SHARED_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final List<String> WRAPPER_KEYS = List.of("data", "items", "results", "records", "list");
    private static final List<String> TOTAL_KEYS = List.of("total", "total_count", "totalCount", "count");

    private final RestClient overrideRestClient;
    private final ConcurrentHashMap<Integer, RestClient> restClientCache = new ConcurrentHashMap<>();

    public HttpQueryEngine() {
        this.overrideRestClient = null;
    }

    public HttpQueryEngine(RestClient restClient) {
        this.overrideRestClient = restClient;
    }

    private RestClient buildRestClient(int timeoutMs) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(SHARED_HTTP_CLIENT);
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return RestClient.builder().requestFactory(factory).build();
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

        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new DataApiException("HTTP source requires 'url' to be configured");
        }

        RestClient restClient = overrideRestClient != null
                ? overrideRestClient
                : restClientCache.computeIfAbsent(timeout, this::buildRestClient);

        try {
            HttpHeaders headers = buildHeaders(source);
            String url = "GET".equals(method) ? buildUrlWithParams(baseUrl, params) : baseUrl;

            Map<String, Object> body = executeRequest(restClient, method, url, params, headers);
            if (body == null) {
                return new QueryResult(List.of(), 0, 0);
            }

            List<Map<String, Object>> data = unwrapResponse(body);
            long total = extractTotal(body, data.size());
            return new QueryResult(data, total, 0);
        } catch (RestClientException e) {
            throw new DataApiException("HTTP forward failed: " + e.getMessage(), e);
        }
    }

    private HttpHeaders buildHeaders(ApiSource source) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (source.headers() != null) {
            source.headers().forEach(headers::set);
        }
        return headers;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeRequest(RestClient restClient, String method,
            String url, Map<String, Object> params, HttpHeaders headers) {
        if ("GET".equals(method)) {
            ResponseEntity<Map> response = restClient.get()
                    .uri(url)
                    .headers(h -> h.addAll(headers))
                    .retrieve()
                    .toEntity(Map.class);
            return response.getBody();
        }
        ResponseEntity<Map> response = restClient.method(HttpMethod.valueOf(method))
                .uri(url)
                .headers(h -> h.addAll(headers))
                .body(params)
                .retrieve()
                .toEntity(Map.class);
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> unwrapResponse(Map<String, Object> body) {
        return WRAPPER_KEYS.stream()
                .map(body::get)
                .filter(val -> val instanceof List<?>)
                .findFirst()
                .map(val -> (List<?>) val)
                .map(list -> list.stream()
                        .filter(item -> item instanceof Map<?, ?>)
                        .map(item -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            ((Map<?, ?>) item).forEach((k, v) -> row.put(String.valueOf(k), v));
                            return row;
                        })
                        .toList())
                .orElse(List.of(body));
    }

    private long extractTotal(Map<String, Object> body, int dataSize) {
        for (String key : TOTAL_KEYS) {
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
