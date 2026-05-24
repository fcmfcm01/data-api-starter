package org.cafeng.openapi.router;

import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Detects path and method collisions between YAML-defined APIs and existing
 * Java {@code @RequestMapping} handlers.
 *
 * <p>Compares YAML paths against registered Spring MVC handler methods,
 * normalizing path-parameter placeholders for matching.</p>
 */
public class PathConflictDetector {

    private final RequestMappingHandlerMapping handlerMapping;

    public PathConflictDetector(RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    public Set<ConflictInfo> detectConflicts(Set<ApiPath> yamlPaths) {
        Set<ConflictInfo> conflicts = new HashSet<>();
        
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();
        
        for (RequestMappingInfo existingMapping : handlerMethods.keySet()) {
            Set<String> existingPaths = existingMapping.getPatternsCondition() != null
                    ? existingMapping.getPatternsCondition().getPatterns()
                    : Set.of();
            
            Set<String> existingMethods = existingMapping.getMethodsCondition() != null
                    ? extractMethodNames(existingMapping.getMethodsCondition().getMethods())
                    : Set.of("*");

            for (ApiPath yamlPath : yamlPaths) {
                for (String existingPath : existingPaths) {
                    if (pathsMatch(existingPath, yamlPath.path())) {
                        for (String yamlMethod : yamlPath.methods()) {
                            if (existingMethods.contains("*") || existingMethods.contains(yamlMethod)) {
                                conflicts.add(new ConflictInfo(
                                        existingPath,
                                        yamlPath.path(),
                                        yamlMethod,
                                        "Path and method conflict with existing Java controller"
                                ));
                            }
                        }
                    }
                }
            }
        }
        
        return conflicts;
    }

    private boolean pathsMatch(String path1, String path2) {
        if (path1.equals(path2)) return true;
        
        String regex1 = toRegex(path1);
        String regex2 = toRegex(path2);
        
        return Pattern.matches(regex1, path2) || Pattern.matches(regex2, path1);
    }

    private String toRegex(String path) {
        return path.replaceAll("\\{[^}]+\\}", "[^/]+");
    }

    private Set<String> extractMethodNames(Set<org.springframework.web.bind.annotation.RequestMethod> methods) {
        Set<String> names = new HashSet<>();
        for (org.springframework.web.bind.annotation.RequestMethod method : methods) {
            names.add(method.name());
        }
        return names;
    }

    public record ApiPath(String path, Set<String> methods) {}
    public record ConflictInfo(String existingPath, String yamlPath, String method, String message) {}
}
