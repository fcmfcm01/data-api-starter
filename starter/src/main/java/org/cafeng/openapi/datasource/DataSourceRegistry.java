package org.cafeng.openapi.datasource;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that maps datasource names to {@link DataSource} instances.
 *
 * <p>Populated at startup by {@code DataApiAutoConfiguration}, which scans
 * all {@code DataSource} beans in the Spring application context.
 * API definitions reference datasources by their Spring bean name.</p>
 */
public class DataSourceRegistry {

    private final Map<String, DataSource> dataSources = new ConcurrentHashMap<>();

    public DataSourceRegistry() {
    }

    public void registerDataSource(String name, DataSource dataSource) {
        dataSources.put(name, dataSource);
    }

    public DataSource getDataSource(String name) {
        DataSource ds = dataSources.get(name);
        if (ds == null) {
            throw new DataSourceNotFoundException(name);
        }
        return ds;
    }

    public boolean hasDataSource(String name) {
        return dataSources.containsKey(name);
    }

    public static class DataSourceNotFoundException extends RuntimeException {
        private final String datasourceName;

        public DataSourceNotFoundException(String datasourceName) {
            super("DataSource not found: " + datasourceName);
            this.datasourceName = datasourceName;
        }

        public String getDatasourceName() {
            return datasourceName;
        }
    }
}