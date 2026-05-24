package org.cafeng.openapi.parser;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Scans the classpath for YAML API definition files.
 *
 * <p>Recursively discovers all {@code .yaml} files under the configured base path
 * (defaults to {@code classpath:apis/}) using Spring's
 * {@code PathMatchingResourcePatternResolver}.</p>
 */
public class YamlDiscovery {

    private final String apisPath;
    private final ResourcePatternResolver resourceResolver;

    public YamlDiscovery(String apisPath) {
        this.apisPath = apisPath != null ? apisPath : "classpath:apis/";
        this.resourceResolver = new PathMatchingResourcePatternResolver();
    }

    public List<Resource> discover() throws IOException {
        try {
            String pattern = apisPath + "**/*.yaml";
            Resource[] resources = resourceResolver.getResources(pattern);
            
            List<Resource> result = new ArrayList<>();
            for (Resource resource : resources) {
                if (resource.isReadable()) {
                    result.add(resource);
                }
            }
            return result;
        } catch (Exception e) {
            // Handle case where directory doesn't exist
            return new ArrayList<>();
        }
    }
}