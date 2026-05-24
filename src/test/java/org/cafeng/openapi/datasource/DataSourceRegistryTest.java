package org.cafeng.openapi.datasource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DataSourceRegistryTest {

    @Test
    void shouldRegisterAndGetDataSource() {
        DataSourceRegistry registry = new DataSourceRegistry();
        javax.sql.DataSource mockDs = createMockDataSource("test");
        
        registry.registerDataSource("test-ds", mockDs);
        
        assertNotNull(registry.getDataSource("test-ds"));
    }

    @Test
    void shouldThrowWhenDataSourceNotFound() {
        DataSourceRegistry registry = new DataSourceRegistry();
        
        assertThrows(DataSourceRegistry.DataSourceNotFoundException.class, 
                () -> registry.getDataSource("nonexistent"));
    }

    @Test
    void shouldCheckExistence() {
        DataSourceRegistry registry = new DataSourceRegistry();
        
        assertFalse(registry.hasDataSource("test"));
        
        registry.registerDataSource("test", createMockDataSource("test"));
        
        assertTrue(registry.hasDataSource("test"));
    }

    @Test
    void shouldStoreMultipleDataSources() {
        DataSourceRegistry registry = new DataSourceRegistry();
        
        registry.registerDataSource("ds1", createMockDataSource("ds1"));
        registry.registerDataSource("ds2", createMockDataSource("ds2"));
        
        assertNotNull(registry.getDataSource("ds1"));
        assertNotNull(registry.getDataSource("ds2"));
    }

    @Test
    void shouldIncludeNameInException() {
        DataSourceRegistry registry = new DataSourceRegistry();
        
        var ex = assertThrows(DataSourceRegistry.DataSourceNotFoundException.class,
                () -> registry.getDataSource("mssql-order"));
        
        assertTrue(ex.getMessage().contains("mssql-order"));
    }

    private javax.sql.DataSource createMockDataSource(String name) {
        return new javax.sql.DataSource() {
            @Override public java.sql.Connection getConnection() { return null; }
            @Override public java.sql.Connection getConnection(String u, String p) { return null; }
            @Override public java.io.PrintWriter getLogWriter() { return null; }
            @Override public void setLogWriter(java.io.PrintWriter out) {}
            @Override public void setLoginTimeout(int seconds) {}
            @Override public int getLoginTimeout() { return 0; }
            @Override public java.util.logging.Logger getParentLogger() { return null; }
            @Override public <T> T unwrap(Class<T> iface) { return null; }
            @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        };
    }
}
