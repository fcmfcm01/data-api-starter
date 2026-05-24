package org.cafeng.openapi.engine;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConditionBuilderTest {

    private final ConditionBuilder builder = new ConditionBuilder();

    @Test
    void shouldAddConditionWhenParamPresent() {
        String sql = "SELECT * FROM orders WHERE 1=1 ${status: AND status = :status}";
        var result = builder.build(sql, Map.of("status", "ACTIVE"));

        assertTrue(result.sql().contains("AND status = :status"));
        assertEquals(Map.of("status", "ACTIVE"), result.parameters());
    }

    @Test
    void shouldSkipConditionWhenParamAbsent() {
        String sql = "SELECT * FROM orders WHERE 1=1 ${status: AND status = :status}";
        var result = builder.build(sql, Map.of());

        assertTrue(result.sql().contains("WHERE 1=1"));
        assertFalse(result.sql().contains("AND status"));
        assertTrue(result.parameters().isEmpty());
    }

    @Test
    void shouldTreatEmptyStringAsAbsent() {
        String sql = "SELECT * FROM orders WHERE 1=1 ${status: AND status = :status}";
        var result = builder.build(sql, Map.of("status", ""));

        assertFalse(result.sql().contains("AND status"));
        assertTrue(result.parameters().isEmpty());
    }

    @Test
    void shouldTreatNullAsAbsent() {
        String sql = "SELECT * FROM orders WHERE 1=1 ${status: AND status = :status}";
        Map<String, Object> params = new HashMap<>();
        params.put("status", null);
        var result = builder.build(sql, params);

        assertFalse(result.sql().contains("AND status"));
        assertTrue(result.parameters().isEmpty());
    }

    @Test
    void shouldTreatZeroAsPresent() {
        String sql = "SELECT * FROM orders WHERE 1=1 ${page: AND page = :page}";
        var result = builder.build(sql, Map.of("page", "0"));

        assertTrue(result.sql().contains("AND page = :page"));
        assertEquals(Map.of("page", "0"), result.parameters());
    }

    @Test
    void shouldHandleMultipleConditions() {
        String sql = "SELECT * FROM orders WHERE 1=1 ${status: AND status = :status} ${type: AND type = :type}";
        var result = builder.build(sql, Map.of("status", "ACTIVE"));

        assertTrue(result.sql().contains("AND status = :status"));
        assertFalse(result.sql().contains("AND type = :type"));
        assertEquals(1, result.parameters().size());
    }

    @Test
    void shouldHandleAllConditionsPresent() {
        String sql = "SELECT * FROM orders WHERE 1=1 ${status: AND status = :status} ${type: AND type = :type}";
        var result = builder.build(sql, Map.of("status", "ACTIVE", "type", "VIP"));

        assertTrue(result.sql().contains("AND status = :status"));
        assertTrue(result.sql().contains("AND type = :type"));
        assertEquals(2, result.parameters().size());
    }

    @Test
    void shouldHandleSqlWithoutConditions() {
        String sql = "SELECT * FROM orders WHERE status = 'ACTIVE'";
        var result = builder.build(sql, Map.of("status", "ACTIVE"));

        assertEquals(sql, result.sql());
        assertTrue(result.parameters().isEmpty());
    }

    @Test
    void shouldHandleNullParametersMap() {
        String sql = "SELECT * FROM orders WHERE 1=1 ${status: AND status = :status}";
        var result = builder.build(sql, null);

        assertFalse(result.sql().contains("AND status"));
    }

    @Test
    void shouldHandleComplexSqlFragment() {
        String sql = "SELECT * FROM orders WHERE 1=1 ${minAmount: AND amount >= :minAmount} ${maxAmount: AND amount <= :maxAmount}";
        var result = builder.build(sql, Map.of("minAmount", 100, "maxAmount", 500));

        assertTrue(result.sql().contains("AND amount >= :minAmount"));
        assertTrue(result.sql().contains("AND amount <= :maxAmount"));
    }
}
