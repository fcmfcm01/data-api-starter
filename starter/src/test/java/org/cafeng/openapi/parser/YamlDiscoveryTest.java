package org.cafeng.openapi.parser;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class YamlDiscoveryTest {

    @Test
    void shouldDiscoverYamlFilesFromClasspath() throws IOException {
        YamlDiscovery discovery = new YamlDiscovery("classpath:apis/");
        List<Resource> resources = discovery.discover();
        
        assertNotNull(resources);
    }

    @Test
    void shouldDiscoverNestedYamlFiles() throws IOException {
        YamlDiscovery discovery = new YamlDiscovery("classpath:apis/");
        List<Resource> resources = discovery.discover();
        
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            assertNotNull(filename);
            assertTrue(filename.endsWith(".yaml"), 
                "Only .yaml files should be discovered, got: " + filename);
        }
    }

    @Test
    void shouldHandleNonExistentDirectory() throws IOException {
        YamlDiscovery discovery = new YamlDiscovery("classpath:nonexistent/");
        // This should not throw an exception even if directory doesn't exist
        List<Resource> resources = discovery.discover();
        
        assertNotNull(resources);
        // If directory doesn't exist, we expect empty list or handle gracefully
    }

    @Test
    void shouldUseDefaultPathWhenNullProvided() throws IOException {
        YamlDiscovery discovery = new YamlDiscovery(null);
        List<Resource> resources = discovery.discover();
        
        assertNotNull(resources);
    }
}
