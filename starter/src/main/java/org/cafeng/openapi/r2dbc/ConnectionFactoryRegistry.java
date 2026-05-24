package org.cafeng.openapi.r2dbc;

import io.r2dbc.spi.ConnectionFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of named {@link ConnectionFactory} instances for R2DBC support.
 *
 * <p>Mirrors {@link org.cafeng.openapi.datasource.DataSourceRegistry} but for
 * reactive R2DBC connection factories. Each API definition references a factory
 * by name via {@code source.datasource}.</p>
 */
public class ConnectionFactoryRegistry {

    private final Map<String, ConnectionFactory> factories = new ConcurrentHashMap<>();

    public ConnectionFactoryRegistry() {
    }

    public void register(String name, ConnectionFactory factory) {
        factories.put(name, factory);
    }

    public ConnectionFactory get(String name) {
        ConnectionFactory cf = factories.get(name);
        if (cf == null) {
            throw new ConnectionFactoryNotFoundException(name);
        }
        return cf;
    }

    public boolean has(String name) {
        return factories.containsKey(name);
    }

    /**
     * Thrown when a requested {@link ConnectionFactory} name is not registered.
     */
    public static class ConnectionFactoryNotFoundException extends RuntimeException {
        private final String name;

        public ConnectionFactoryNotFoundException(String name) {
            super("ConnectionFactory not found: " + name);
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
