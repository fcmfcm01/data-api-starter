package org.cafeng.openapi.scope;

import java.util.Arrays;
import java.util.List;

public final class ScopeUtils {

    private ScopeUtils() {
    }

    public static List<String> parseScopeString(String scopeClaim) {
        if (scopeClaim == null || scopeClaim.isBlank()) {
            return List.of();
        }
        return Arrays.stream(scopeClaim.split("[\\s,]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
