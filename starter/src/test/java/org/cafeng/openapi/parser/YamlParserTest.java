package org.cafeng.openapi.parser;

import org.cafeng.openapi.definition.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class YamlParserTest {

    private final YamlParser parser = new YamlParser();

    @Test
    void shouldParseQueryOrdersYaml() throws Exception {
        var resource = new ClassPathResource("apis/query-orders.yaml");
        var api = parser.parse(resource);

        assertEquals("query-orders", api.id());
        assertEquals("Query Orders", api.name());
        assertEquals("/v1/orders", api.path());
        assertEquals("GET", api.method());
        assertNotNull(api.parameters());
        assertEquals(3, api.parameters().size());
        assertNotNull(api.source());
        assertEquals("jdbc", api.source().type());
        assertEquals("dataSource", api.source().datasource());
        assertTrue(api.source().query().contains("${status:"));
        assertEquals("page", api.response().type());
        assertNotNull(api.response().fields());
        assertEquals(3, api.response().fields().size());
        assertNotNull(api.scopes());
        assertNotNull(api.sla());
        assertEquals(3000, api.sla().timeout());
    }

    @Test
    void shouldParseCreateOrderYaml() throws Exception {
        var resource = new ClassPathResource("apis/create-order.yaml");
        var api = parser.parse(resource);

        assertEquals("create-order", api.id());
        assertEquals("POST", api.method());
        assertEquals(2, api.parameters().size());

        var orderNo = api.parameters().get(0);
        assertEquals("orderNo", orderNo.name());
        assertEquals("body", orderNo.in());
        assertTrue(orderNo.required());

        assertTrue(api.source().query().contains("INSERT INTO"));
        assertEquals("single", api.response().type());
    }

    @Test
    void shouldParseListProductsYaml() throws Exception {
        var resource = new ClassPathResource("apis/subdir/list-products.yaml");
        var api = parser.parse(resource);

        assertEquals("list-products", api.id());
        assertEquals("GET", api.method());
        assertEquals("list", api.response().type());
        assertTrue(api.parameters().isEmpty());
    }

    @Test
    void shouldParseParameterEnumValues() throws Exception {
        var resource = new ClassPathResource("apis/query-orders.yaml");
        var api = parser.parse(resource);

        var statusParam = api.parameters().stream()
                .filter(p -> "status".equals(p.name()))
                .findFirst()
                .orElseThrow();

        assertNotNull(statusParam.enumValues());
        assertEquals(3, statusParam.enumValues().size());
        assertTrue(statusParam.enumValues().contains("ACTIVE"));
    }

    @Test
    void shouldParseResponseFieldsWithScopes() throws Exception {
        var resource = new ClassPathResource("apis/query-orders.yaml");
        var api = parser.parse(resource);

        var fields = api.response().fields();
        assertEquals("orderNo", fields.get(0).name());
        assertEquals("basic", fields.get(0).scope());
    }

    @Test
    void shouldParseScopesAsMap() throws Exception {
        var resource = new ClassPathResource("apis/query-orders.yaml");
        var api = parser.parse(resource);

        assertNotNull(api.scopes());
        assertEquals("basic", api.scopes().get("order.read"));
        assertEquals("detail", api.scopes().get("order.detail"));
    }

    @Test
    void shouldParseTestApiYaml() throws Exception {
        // test-api.yaml has a valid api section, should parse successfully
        var resource = new ClassPathResource("apis/test-api.yaml");
        var api = parser.parse(resource);
        assertEquals("test-api", api.id());
    }

    @Test
    void shouldHandleNullOptionalFields() throws Exception {
        var resource = new ClassPathResource("apis/subdir/list-products.yaml");
        var api = parser.parse(resource);

        assertNull(api.sla());
        assertTrue(api.scopes().isEmpty());
    }
}
