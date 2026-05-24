package org.cafeng.openapi.autoconfigure;

import org.cafeng.openapi.definition.ApiDefinition;
import org.cafeng.openapi.error.DataApiException;
import org.cafeng.openapi.openapi.OpenApiGenerator;
import org.cafeng.openapi.parser.YamlDiscovery;
import org.cafeng.openapi.parser.YamlLint;
import org.cafeng.openapi.parser.YamlParser;
import org.cafeng.openapi.registry.ApiDefinitionRegistry;
import org.cafeng.openapi.router.DynamicRouterRegistrar;
import org.cafeng.openapi.router.PathConflictDetector;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates the four-phase startup sequence: discover, parse, lint, register.
 *
 * <p>Runs at {@code @PostConstruct} time. Discovers YAML resources, parses them
 * into {@code ApiDefinition} objects, runs lint checks, detects path conflicts
 * with existing Java controllers, and registers validated APIs as Spring MVC routes.</p>
 */
public class DataApiInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataApiInitializer.class);

    private final DataApiProperties properties;
    private final DynamicRouterRegistrar routerRegistrar;
    private final OpenApiGenerator openApiGenerator;
    private final ApiDefinitionRegistry apiDefinitionRegistry;
    private final YamlDiscovery yamlDiscovery;
    private final YamlParser yamlParser;
    private final YamlLint yamlLint;
    private final PathConflictDetector pathConflictDetector;

    public DataApiInitializer(
            DataApiProperties properties,
            DynamicRouterRegistrar routerRegistrar,
            OpenApiGenerator openApiGenerator,
            ApiDefinitionRegistry apiDefinitionRegistry,
            YamlDiscovery yamlDiscovery,
            YamlParser yamlParser,
            YamlLint yamlLint,
            PathConflictDetector pathConflictDetector) {
        this.properties = properties;
        this.routerRegistrar = routerRegistrar;
        this.openApiGenerator = openApiGenerator;
        this.apiDefinitionRegistry = apiDefinitionRegistry;
        this.yamlDiscovery = yamlDiscovery;
        this.yamlParser = yamlParser;
        this.yamlLint = yamlLint;
        this.pathConflictDetector = pathConflictDetector;
    }

    @PostConstruct
    public void initialize() {
        try {
            log.info("Initializing Data API Starter...");

            List<Resource> resources = yamlDiscovery.discover();

            log.info("Found {} YAML API definitions", resources.size());

            // Phase 1: Parse all YAML files
            List<ApiDefinition> definitions = new ArrayList<>();
            for (Resource resource : resources) {
                try {
                    var apiDefinition = yamlParser.parse(resource);
                    definitions.add(apiDefinition);
                } catch (Exception e) {
                    log.error("Failed to parse API from {}", resource.getFilename(), e);
                    throw new DataApiException("Failed to initialize API: " + resource.getFilename(), e);
                }
            }

            // Phase 2: Lint all definitions
            List<String> lintErrors = yamlLint.lint(definitions);
            if (!lintErrors.isEmpty()) {
                for (String error : lintErrors) {
                    log.error("Lint error: {}", error);
                }
                throw new DataApiException("Lint validation failed: " + String.join("; ", lintErrors));
            }

            // Phase 3: Check path conflicts with existing @RequestMapping
            Set<PathConflictDetector.ApiPath> yamlPaths = definitions.stream()
                    .map(def -> new PathConflictDetector.ApiPath(
                            def.path(),
                            Set.of(def.method().toUpperCase())))
                    .collect(Collectors.toSet());

            Set<PathConflictDetector.ConflictInfo> conflicts = pathConflictDetector.detectConflicts(yamlPaths);
            if (!conflicts.isEmpty()) {
                for (var conflict : conflicts) {
                    log.error("Path conflict: {} {} conflicts with {}",
                            conflict.method(), conflict.yamlPath(), conflict.existingPath());
                }
                throw new DataApiException("Path conflict detected: " + conflicts.iterator().next().toString());
            }

            // Phase 4: Register all APIs
            for (ApiDefinition apiDefinition : definitions) {
                log.info("Registering API: {} at {} {}",
                        apiDefinition.id(), apiDefinition.method(), apiDefinition.path());
                apiDefinitionRegistry.register(apiDefinition);
                routerRegistrar.registerApi(apiDefinition);
            }

            log.info("Data API Starter initialized with {} APIs", definitions.size());

        } catch (Exception e) {
            log.error("Failed to initialize Data API Starter", e);
            throw new DataApiException("Data API Starter initialization failed", e);
        }
    }
}
